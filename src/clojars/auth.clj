(ns clojars.auth
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [clojars.db :refer [group-membernames find-user-by-user-or-email]]))

(def credentials
  (partial creds/bcrypt-credential-fn
           (fn [id]
             (when-let [user (find-user-by-user-or-email id)]
               (select-keys user [:username :password])))))

(defmacro with-account [body]
  `(friend/authenticated (try-account ~body)))

(defmacro try-account [body]
  `(let [~'account (:username (friend/current-authentication))]
     ~body))

(defn authorized? [account group]
  (let [names# (group-membernames group)]
    (or (some #{account} names#) (empty? names#))))

(defmacro require-authorization [group & body]
  `(if (authorized? ~'account ~group)
       (do ~@body)
       (friend/throw-unauthorized friend/*identity*)))