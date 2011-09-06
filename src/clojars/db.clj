(ns clojars.db
  (:use [clojars.config :only [config]]
        clojure.contrib.sql)
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import java.security.MessageDigest
           java.sql.Timestamp
           java.io.File))

(def ^{:private true} ssh-options
  "no-agent-forwarding,no-port-forwarding,no-pty,no-X11-forwarding")

(def reserved-names
  #{"clojure" "clojars" "clojar" "register" "login"
    "pages" "logout" "password" "username" "user"
    "repo" "repos" "jar" "jars" "about" "help" "doc"
    "docs" "images" "js" "css" "maven" "api"
    "download" "create" "new" "upload" "contact" "terms"
    "group" "groups" "browse" "status" "blog" "search"
    "email" "welcome" "devel" "development" "test" "testing"
    "prod" "production" "admin" "administrator" "root"
    "webmaster" "profile" "dashboard" "settings" "options"
    "index" "files" "releases" "snapshots"})

(def ^{:private true} constituent-chars
  (->> [[\a \z] [\A \Z] [\0 \9]]
       (mapcat (fn [[x y]] (range (int x) (inc (int y)))))
       (map char)
       vec))

(defn rand-string
  "Generates a random string of [A-z0-9] of length n."
  [n]
  (apply str (repeatedly n #(rand-nth constituent-chars))))

(defn write-key-file [path]
  (locking (:key-file config)
   (let [new-file (File. (str path ".new"))]
     (with-query-results rs ["select username, ssh_key from users"]
       (with-open [f (io/writer new-file)]
         (doseq [{:keys [user ssh_key]} rs]
           (.write f (str "command=\"ng --nailgun-port 8700 clojars.scp " user
                            "\"," ssh-options " "
                            (.replaceAll (.trim ssh_key)
                                         "[\n\r\0]" "")
                            "\n")))))
     (.renameTo new-file (File. path)))))

(defn db-middleware
  [handler]
  (fn [request]
    (with-connection (:db config) (handler request))))

(defmacro with-db
  [& body]
  `(with-connection (:db config)
     ~@body))

(defn sha1 [& s]
  (when-let [s (seq s)]
    (let [md (MessageDigest/getInstance "SHA")]
      (.update md (.getBytes (apply str s)))
      (format "%040x" (java.math.BigInteger. 1 (.digest md))))))

(defn find-user [username]
  (with-query-results rs ["select * from users where username = ?" username]
    (first rs)))

(defn find-groups [username]
  (with-query-results rs ["select * from groups where username = ?" username]
    (doall (map :name rs))))

(defn group-members [group]
  (with-query-results rs ["select * from groups where name like ?" group]
    (doall (map :username rs))))

(defn auth-user [username pass]
  (with-query-results rs
    ["select * from users where (username = ? or email = ?)" username username]
    (first (filter #(= (:password %) (sha1 (:salt %) pass)) rs))))

(defn jars-by-user [username]
  (with-query-results rs [(str "SELECT DISTINCT ON (group_name, jar_name) * "
                               "FROM jars WHERE username = ?") username]
    (vec rs)))

(defn jars-by-group [group]
  (with-query-results rs [(str "SELECT DISTINCT ON (jar_name) * "
                               "FROM jars WHERE group_name = ?")
                          group]
    (vec rs)))

(defn recent-jars []
  (with-query-results rs
    [(str "SELECT DISTINCT ON (created, group_name, jar_name) * from jars "
          "ORDER BY created DESC LIMIT 5")]
    (vec rs)))

(defn find-canon-jar [jarname]
  (with-query-results rs
      [(str "select * from jars where "
            "jar_name = ? and group_name = ? "
            "order by created desc limit 1")
       jarname jarname]
    (first rs)))

(defn find-jar
  ([jarname]
     (with-query-results rs ["select * from jars where jar_name = ?" jarname]
       (first rs)))
  ([group jarname]
     (with-query-results rs [(str "select * from jars where group_name = ? and "
                                  "jar_name = ? order by created desc "
                                  "limit 1") group jarname]
       (first rs))))

(defn add-user [email username password ssh-key]
  (let [salt (rand-string 16)]
    (insert-values
     :users
     [:email :username :password :salt :ssh_key :created]
     [email username (sha1 salt password) salt ssh-key
      (Timestamp. (System/currentTimeMillis))])
    (insert-values
     :groups
     [:name :username]
     [(str "org.clojars." username) username])
    (write-key-file (:key-file config))))

(defn update-user [account email username password ssh-key]
  (let [salt (rand-string 16)]
   (update-values
    :users ["username=?" account]
    {:email email
     :username username
     :salt salt
     :password (sha1 salt password)
     :ssh_key ssh-key})
   (write-key-file (:key-file config))))

(defn add-member [group username]
  (insert-records :groups
                  {:name group
                   :username username}))

(defn check-and-add-group [account group jar]
  (when-not (re-matches #"^[a-z0-9-_.]+$" group)
    (throw (Exception. (str "Group names must consist of lowercase "
                            "letters, numbers, hyphens, underscores "
                            "and full-stops."))))
  (let [members (group-members group)]
    (if (empty? members)
      (if (reserved-names group)
        (throw (Exception. (str "The group name " group " is already taken.")))
        (add-member group account))
      (when-not (some #{account} members)
        (throw (Exception. (str "You don't have access to the "
                                group " group.")))))))

(defn add-jar [account jarmap & [check-only]]
  (when-not (re-matches #"^[a-z0-9-_.]+$" (:name jarmap))
    (throw (Exception. (str "Jar names must consist solely of lowercase "
                            "letters, numbers, hyphens and underscores."))))

  (with-connection (:db config)
    (transaction
     (when check-only (set-rollback-only))
     (check-and-add-group account (:group jarmap) (:name jarmap))
     (insert-records
      :jars
      {:group_name (:group jarmap)
       :jar_name   (:name jarmap)
       :version    (:version jarmap)
       :username   account
       :created    (Timestamp. (System/currentTimeMillis))
       :description (:description jarmap)
       :homepage   (:homepage jarmap)
       :authors    (str/join ", " (map #(.replace % "," "")
                                       (:authors jarmap)))}))))

(defn quote-hyphenated
  "Wraps hyphenated-words in double quotes."
  [s]
  (str/replace s #"\w+(-\w+)+" "\"$0\""))

(defn search-jars [query & [offset]]
  ;; TODO make search less stupid, figure out some relevance ranking
  ;; scheme, do stopwords etc.
  (with-query-results rs
      [(str "select jar_name, group_name from search where "
            "content match ? "
	    "order by rowid desc "
            "limit 100 "
            "offset ?")
       (quote-hyphenated query)
       (or offset 0)]
    ;; TODO: do something less stupidly slow
    (vec (map #(find-jar (:group_name %) (:jar_name %)) rs))))

(comment
  (with-connection (:db config)
    (add-jar "atotx" {:name "test3" :group "test3" :version "1.0"
                      :description "An dog awesome and non-existent test jar."
                      :homepage "http://clojars.org/"
                      :authors ["Alex Osborne" "a little fish"]}))
  (with-connection (:db config) (find-user "atox")))
