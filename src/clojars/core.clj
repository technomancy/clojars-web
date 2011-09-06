(ns clojars.core
  (:require [clojars.web :as web]
            [clojars.scp :as scp])
  (:gen-class))

(defn -main
  ([]
     (-main (or (System/getenv "PORT") "8080")
            (or (System/getenv "SCP_PORT") "3333")))
  ([http-port scp-port]
     (println "clojars-web: starting jetty on port" http-port)
     (web/-main http-port)
     (println "clojars-web: starting SCP on 127.0.0.1 port " scp-port)
     (scp/-main scp-port)))
