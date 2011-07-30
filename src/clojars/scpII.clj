(ns clojars.scpII
  (:import (org.apache.sshd SshServer)
           (org.apache.sshd.server PublickeyAuthenticator FileSystemAware Command)
           (org.apache.sshd.server.command ScpCommand ScpCommandFactory)
           (org.apache.sshd.server.keyprovider SimpleGeneratorHostKeyProvider)))

(def pem-files "/Users/hiredman/Downloads/one.pem")

(defn launch-ssh []
  (let [sshd (doto (SshServer/setUpDefaultServer)
               (.setPort 3333)
               (.setPublickeyAuthenticator
                (reify
                  PublickeyAuthenticator
                  (authenticate [_ username key serssion]
                    true)))
               (.setCommandFactory
                (proxy [ScpCommandFactory] []
                  (createCommand [command]
                    (let [in (promise)
                          out (promise)
                          err (promise)
                          thread (promise)]
                      (reify
                        Command
                        (setInputStream [_ ins])
                        (setOutputStream [_ outs])
                        (setErrorStream [_ errs])
                        (setExitCallback [_ callback])
                        (start [this env]
                          (println "THREAD" (bean env))
                          (deliver thread (.start (Thread. this))))
                        (destroy [_])
                        Runnable
                        (run [_]
                          (println "RUNNING")))))))
               (.setKeyPairProvider
                (SimpleGeneratorHostKeyProvider. pem-files)))]
    (doto sshd .start)))

