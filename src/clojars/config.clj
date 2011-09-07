(ns clojars.config
  (:require [clojure.string :as str])
  (:import (java.net URI)))

(defn database-resource []
  (let [url (URI. (or (System/getenv "DATABASE_URL")
                      "//localhost:5432/clojars"))
        host (.getHost url)
        port (if (pos? (.getPort url)) (.getPort url) 5432)
        path (.getPath url)]
    (merge
      {:subname (str "//" host ":" port path)}
      (if-let [user-info (.getUserInfo url)]
        {:user (first (str/split user-info #":"))
         :password (second (str/split user-info #":"))}
        {:user "clojars", :password "typhoons"}))))

(def config
  {:db (merge {:classname "org.postgresql.Driver"
               :subprotocol "postgresql"}
              (database-resource))
   :key-file "data/authorized_keys"
   :ssh-pem "data/key.pem"
   :repo "file:///tmp/clojars-repo"})
