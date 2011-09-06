(ns clojars.core
  (:use [ring.adapter.jetty :only [run-jetty]]
        [clojars.web :only [clojars-app]]
        [clojars.scpII :only [launch-ssh]])
  (:import com.martiansoftware.nailgun.NGServer
           java.net.InetAddress)
  (:gen-class))

(defn -main
  ([]
     (-main (or (System/getenv "PORT") "8080")
            (or (System/getenv "SCP_PORT") "3333")))
  ([http-port]
     (-main http-port false))
  ([http-port scp-port]
     (println "clojars-web: starting jetty on port" http-port)
     (run-jetty clojars-app {:port (Integer/parseInt http-port) :join? false})
     (when scp-port
       (println "clojars-web: starting SCP on 127.0.0.1 port " scp-port)
       (launch-ssh (Integer/parseInt scp-port)))))

;; (defonce server (run-jetty #'clojars-app {:port 8080 :join? false}))
;; (.stop server)
