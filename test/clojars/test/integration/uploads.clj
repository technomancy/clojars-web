(ns clojars.test.integration.uploads
  (:require [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojars.config :refer [config]]
            [clojars.web :refer [clojars-app]]
            [clojars.test.integration.steps :refer :all]
            [clojars.test.test-helper :as help]
            [clojure.java.io :as io]
            [cemerick.pomegranate.aether :as aether]
            [ring.adapter.jetty :as jetty]
            [net.cgrand.enlive-html :as enlive]))

(declare test-port)

(defn- run-test-app
  [f]
  (let [server (jetty/run-jetty #'clojars-app {:port 0 :join? false})
        port (-> server .getConnectors first .getLocalPort)]
    (with-redefs [test-port port]
      (try
        (f)
        (finally
         (.stop server))))))

(use-fixtures :once run-test-app)
(help/use-fixtures)

(deftest user-can-register-and-deploy
  (-> (session clojars-app)
      (register-as "dantheman" "test@example.org" "password" ""))
  (help/delete-file-recursively help/local-repo)
  (help/delete-file-recursively help/local-repo2)
  (aether/deploy
   :coordinates '[org.clojars.dantheman/test "1.0.0"]
   :jar-file (io/file (io/resource "test.jar"))
   :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
   :repository {"test" {:url (str "http://localhost:" test-port "/repo")
                        :username "dantheman"
                        :password "password"}}
   :local-repo help/local-repo)
  (is (= '{[org.clojars.dantheman/test "1.0.0"] nil}
         (aether/resolve-dependencies
          :coordinates '[[org.clojars.dantheman/test "1.0.0"]]
          :repositories {"test" {:url
                                 (str "http://localhost:" test-port "/repo")}}
          :local-repo help/local-repo2)))
  (-> (session clojars-app)
      (visit "/groups/org.clojars.dantheman")
      (has (status? 200))
      (within [:article [:ul enlive/last-of-type] [:li enlive/last-child] :a]
              (has (text? "dantheman")))
      (follow "org.clojars.dantheman/test")))

(deftest user-can-deploy-to-new-group
   (-> (session clojars-app)
       (register-as "dantheman" "test@example.org" "password" ""))
   (help/delete-file-recursively help/local-repo)
   (help/delete-file-recursively help/local-repo2)
   (aether/deploy
    :coordinates '[fake/test "1.0.0"]
    :jar-file (io/file (io/resource "test.jar"))
    :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
    :repository {"test" {:url (str "http://localhost:" test-port "/repo")
                         :username "dantheman"
                         :password "password"}}
    :local-repo help/local-repo)
   (is (= '{[fake/test "1.0.0"] nil}
          (aether/resolve-dependencies
           :coordinates '[[fake/test "1.0.0"]]
           :repositories {"test" {:url
                                  (str "http://localhost:" test-port "/repo")}}
           :local-repo help/local-repo2)))
   (-> (session clojars-app)
       (visit "/groups/fake")
       (has (status? 200))
       (within [:article [:ul enlive/last-of-type] [:li enlive/last-child] :a]
               (has (text? "dantheman")))
       (follow "fake/test")
       (within [:article [:p (enlive/nth-of-type 2)]]
               (has (text? "https://example.org")))))

(deftest user-cannot-deploy-to-groups-without-permission
  (-> (session clojars-app)
      (register-as "dantheman" "test@example.org" "password" valid-ssh-key))
  (-> (session clojars-app)
      (register-as "fixture" "fixture@example.org" "password" valid-ssh-key))
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Unauthorized"
        (aether/deploy
         :coordinates '[org.clojars.fixture/test "1.0.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" test-port "/repo")}}
         :local-repo help/local-repo))))

(deftest anonymous-cannot-deploy
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Unauthorized"
        (aether/deploy
         :coordinates '[fake/test "1.0.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" test-port "/repo")}}
         :local-repo help/local-repo))))

(deftest bad-login-cannot-deploy
  (is (thrown? org.sonatype.aether.deployment.DeploymentException
        (aether/deploy
         :coordinates '[fake/test "1.0.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" test-port "/repo")
                              :username "guest"
                              :password "password"}}
         :local-repo help/local-repo))))