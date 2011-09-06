(ns clojars.core
  (:use [ring.adapter.jetty :only [run-jetty]]
        [clojars.web :only [clojars-app]]
        [clojars.scpII :only [launch-ssh]])
  (:import com.martiansoftware.nailgun.NGServer
           java.net.InetAddress)
  (:gen-class))

(defn -main
  ([]
     (.println System/err "Usage: clojars.core http-port nailgun-port")
     (.println System/err "   eg: clojars.core 8080 8701")
     (System/exit 1))
  ([http-port scp-port]
     (println "clojars-web: starting jetty on port" http-port)
     (run-jetty clojars-app {:port (Integer/parseInt http-port) :join? false})
     (println "clojars-web: starting SCP on 127.0.0.1 port " scp-port)
     (launch-ssh (Integer/parseInt scp-port))))

;; (defonce server (run-jetty #'clojars-app {:port 8080 :join? false}))
;; (.stop server)
