(ns clojars.repo
  (:require [clojars.auth :refer [with-account require-authorization]]
            [clojars.db :refer [find-jar add-jar update-jar]]
            [clojars.config :refer [config]]
            [clojars.maven :as maven]
            [compojure.core :refer [defroutes PUT ANY]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [ring.util.codec :as codec]
            [ring.util.response :as response])
  (:import java.io.StringReader))

(defn save-to-file [sent-file input]
  (-> sent-file
      .getParentFile
      .mkdirs)
  (io/copy input sent-file))

(defroutes routes
  (PUT ["/:group/:artifact/:file"
        :group #".+" :artifact #"[^/]+" :file #"maven-metadata\.xml[^/]*"]
       {body :body {:keys [group artifact file]} :params}
       (with-account
         (require-authorization
          (string/replace group "/" ".")
          (save-to-file (io/file (config :repo) group artifact file)
                        body)
          {:status 201 :headers {} :body nil})))
  (PUT ["/:group/:artifact/:version/:file"
        :group #".+" :artifact #"[^/]+" :version #"[^/]+" :file #"[^/]+"]
       {body :body {:keys [group artifact version file]} :params}
       (let [groupname (string/replace group "/" ".")]
         (with-account
           (require-authorization
            groupname
            (try
              (let [info {:group groupname
                          :name  artifact
                          :version version}]
                (if (.endsWith file ".pom")
                  (let [contents (slurp body)
                        pom-info (merge (maven/pom-to-map
                                         (StringReader. contents)) info)]
                    (if (find-jar groupname artifact version)
                      (update-jar account pom-info)
                      (add-jar account pom-info))
                    (save-to-file (io/file (config :repo) group
                                           artifact version file)
                                  contents))
                  (do
                    (when-not (find-jar groupname artifact version)
                      (add-jar account info))
                    (save-to-file (io/file (config :repo) group
                                           artifact version file)
                                  body))))
              {:status 201 :headers {} :body nil}
              (catch Exception e
                {:status 403 :headers {} :body (.getMessage e)})))))))

(defn wrap-file [app dir]
  (fn [req]
    (if-not (= :get (:request-method req))
      (app req)
      (let [path (codec/url-decode (:path-info req))]
        (or (response/file-response path {:root dir})
            (app req))))))