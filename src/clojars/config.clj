(ns clojars.config)

(def config
  {:db {:classname "org.sqlite.JDBC"
        :subprotocol "sqlite"
        :subname "data/db"}
   :key-file "data/authorized_keys"
   :repo "repo"})
