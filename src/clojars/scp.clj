(ns clojars.scp
  (:refer-clojure :exclude [read-line])
  (:use [clojure.java.io :only [copy file]]
        [clojars.config :only [config]])
  (:require [clojure.string :as string]
            [clojars.maven   :as maven])
  (:import (org.apache.sshd SshServer)
           (org.apache.sshd.server PublickeyAuthenticator FileSystemAware
                                   Command)
           (org.apache.sshd.server.command ScpCommand ScpCommandFactory)
           (org.apache.sshd.server.keyprovider SimpleGeneratorHostKeyProvider)
           (java.io InputStreamReader BufferedReader IOException
                    FilterInputStream File)
           (java.util UUID)
           (org.apache.commons.io.input BoundedInputStream)))

(def max-file-size 20485760)

(def allowed-suffixes #{"pom" "xml" "jar" "sha1" "md5"})

(defn read-metadata [f default-group]
  (let [model (maven/read-pom (:file f))
        jarmap (maven/model-to-map model)]
    [[model jarmap]]))

(defn jar-names
  "Construct a few possible name variations a jar might have."
  [jarmap]
  [(str (:name jarmap) "-" (:version jarmap) ".jar")
   (str (:name jarmap) ".jar")])

(defmacro with-temp-files [[name & names] & body]
  `(let [~name (File. ~(str "/tmp/" (UUID/randomUUID)))]
     (try
       ~(if (seq names)
          `(with-open ~(vec names)
             ~@body)
          `(do ~@body))
       ~(when-not (:keep (meta name))
          `(finally
            (doall (map #(.delete %) (reverse (file-seq ~name)))))))))

(defn preprocess-command [command]
  (loop [[x & xs] (rest (.split command " ")) accum []]
    (cond
     (not (.startsWith (.trim x) "-"))
     {:flags accum :files (string/join \space (conj xs x))}
     (= (.trim x) "--")
     (recur xs accum)
     :else
     (recur xs (conj accum x)))))

(defn process-command [{:keys [flags files]}]
  {:files files
   :flags (->> (for [f flags]
                 [(keyword (apply str (drop 1 f))) true])
               (into {}))})

(def OK 0)
(def WARNING 1)
(def ERROR 2)

(def *log*)

(defn send-ack [outs]
  (.write outs OK)
  (.flush outs))

(defn read-line [ins]
  (let [rdr (-> ins InputStreamReader. BufferedReader.)]
    (if-let [line (.readLine rdr)]
      line
      (throw (IOException. "End of stream")))))

(defn read-ack [ins can-eof?]
  (let [c (.read ins)]
    (condp = c
      -1 (when-not can-eof?
           (throw (java.io.EOFException.)))
      WARNING (.warn *log* (str "Received warning: " (read-line ins)))
      ERROR (throw (IOException.
                    (str "Received nack: " (read-line ins))))
      nil)
    c))

(defn write-file [in out header cmd upload-dir accepted-types]
  (when-not (.startsWith header "C")
    (throw
     (IOException.
      (format "Expected a C message but got '%s'" header))))
  (let [perms (subs header 1 5)
        length (Long/parseLong (subs header 6 (.indexOf header (int \space) 6)))
        file-name (subs header (inc (.indexOf header (int \space) 6)))
        out-file (file upload-dir (str (UUID/randomUUID)))]
    (when-not (some #(.endsWith file-name %) accepted-types)
      (throw
       (IllegalArgumentException.
        (str "File type not allowed: " file-name))))
    (when (> length max-file-size)
      (throw
       (IllegalArgumentException.
        (str "File too large: " file-name " " length))))
    (send-ack out)
    (copy (BoundedInputStream. in length) out-file)
    (send-ack out)
    (read-ack in false)
    {:file-name file-name
     :file out-file
     :permissions perms
     :file-length length}))

(defn deploy [username files]
  (let [account username
        act-group (str "org.clojars." account)
        metadata (filter #(or (.endsWith (:file-name %) ".xml")
                              (.endsWith (:file-name %) ".pom")) files)
        jars (filter #(.endsWith (:file-name %) ".jar") files)
        jarfiles (into {} (map (juxt :file-name :file) files))]
    (doseq [metafile metadata
            :when (not= (:file-name metafile) "maven-metadata.xml")
            [model jarmap] (read-metadata metafile act-group)
            :let [names (jar-names jarmap)]]
      (if-let [jarfile (some jarfiles names)]
        (do
          (.println *err* (str "\nDeploying " (:group jarmap) "/"
                               (:name jarmap) " " (:version jarmap)))
          (maven/deploy-model jarfile model (str "file://" (:repo config))))
        (throw (Exception. (str "You need to give me one of: " names)))))
    (.println *err* (str "\nSuccess! Your jars are now available from "
                         "http://clojars.org/"))
    (.flush *err*)))

(defn run-scp [& {:keys [log err cmd in out env callback]}]
  (with-temp-files [upload-dir]
    (.mkdir upload-dir)
    (binding [*log* log
              *err* (java.io.PrintWriter. err true)]
      (condp #(%2 %1) (:flags cmd)
        :t (letfn [(k1 [{:keys [line dir? c] :as m}]
                     (cond
                      (= -1 c) ::end
                      (= (char c) \D) (k2 (assoc m
                                            :dir? true))
                      (= (char c) \C) (k3 (assoc m
                                            :line (str (char c)
                                                       (read-line
                                                        in))))
                      (= (char c) \E)  (read-line in)
                      :else (k3 m)))
                   (k2 [{:keys [line dir? c] :as m}]
                     (.info *log* (str `k2 " " (pr-str m) " " (char c)))
                     (condp #(= (int %1) (int %2)) c
                       \C (k3 (assoc m
                                :line (str (char c) (read-line in))))
                       \E (read-line in)
                       :else (k3 m)))
                   (k3 [{:keys [line dir? c] :as m}]
                     (.info *log* (str `k3 " " (pr-str m) " " (char c)))
                     (if (and dir? (:r (:flags cmd)))
                       (.info *log* "DIRECTORY")
                       (do
                         (.info *log* "FILE")
                         (write-file in out line cmd upload-dir
                                     allowed-suffixes))))]
             (send-ack out)
             (deploy
              (get (.getEnv env) "USER")
              (loop [accum []]
                (let [r (k1 {:c (read-ack in true)})]
                  (if (= r ::end)
                    accum
                    (recur (conj accum r))))))))
      (.onExit callback 0 "GO AWAY"))))

(defn scp-command [command]
  (binding [*log* (org.slf4j.LoggerFactory/getLogger
                   (str 'clojars.scp))]
    (let [in (promise)
          out (promise)
          err (promise)
          callback (promise)
          thread (promise)
          env (promise)
          cmd (process-command command)
          log *log*]
      (reify
        Command
        (setInputStream [_ ins]
          (deliver in ins))
        (setOutputStream [_ outs]
          (deliver out outs))
        (setErrorStream [_ errs]
          (deliver err errs))
        (setExitCallback [_ callbacka]
          (deliver callback callbacka))
        (start [this enva]
          (deliver env enva)
          (deliver thread (doto (Thread. this (pr-str cmd)) .start)))
        (destroy [_])
        Runnable
        (run [_]
          (run-scp :log log :err @err :cmd cmd :in @in :out @out :env @env
                   :callback @callback))))))

(defn launch-ssh [port]
  (let [sshd (doto (SshServer/setUpDefaultServer)
               (.setPort port)
               (.setPublickeyAuthenticator
                (reify
                  PublickeyAuthenticator
                  (authenticate [_ username key session]
                    (println username key session)
                    true)))
               (.setCommandFactory
                (proxy [ScpCommandFactory] []
                  (createCommand [command]
                    (when-not (.startsWith (.trim command) "scp")
                      (throw
                       (IllegalArgumentException.
                        "Unknown command, does not begin with 'scp'")))
                    (scp-command (preprocess-command command)))))
               (.setKeyPairProvider
                (SimpleGeneratorHostKeyProvider. (:ssh-pem config))))]
    (doto sshd .start)))

(defn -main [& [scp-port]]
  (let [port (or scp-port (System/getenv "PORT") "3333")]
    (launch-ssh (Integer/parseInt port))))
