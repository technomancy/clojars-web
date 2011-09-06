(ns clojars.sample-data
  (:use [clojars.config :only [config]]
        [clojure.contrib.sql :only [with-connection delete-rows]])
  (:require [clojars.db :as db]
            [clojure.java.io :as io]))

(defn -main
  ([] (-main false))
  ([purge-first?]
     (with-connection (:db config)
       (when purge-first?
         (doseq [table ["users" "jars" "groups"]]
           (delete-rows table ["1=1"])))
       (db/add-user "quentin@example.com" "quentin" "password"
                    (slurp (io/resource "sample-key.pub")))
       (db/add-jar "quentin" {:name "katana"
                              :group "katana"
                              :version "1.0.0"
                              :description "For slicing!"
                              :homepage "http://cut.you"
                              :authors []
                              :dependencies []}))))
