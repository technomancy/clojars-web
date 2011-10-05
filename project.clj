(defproject clojars-web "0.6.2"
  :main clojars.core
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.apache.maven/maven-ant-tasks "2.0.10"]
                 [org.apache.maven.wagon/wagon-provider-api "1.0"]
                 [org.apache.maven/maven-artifact-manager "2.2.1"]
                 [org.apache.maven/maven-model "2.2.1"]
                 [org.apache.maven/maven-project "2.2.1"]
                 [compojure "0.5.2"]
                 [ring/ring-jetty-adapter "0.3.1"]
                 [hiccup "0.3.0"]
                 [org.clojars.ato/nailgun "0.7.1"]
                 [postgresql "8.4-702.jdbc4"]
                 [org.clojure/java.jdbc "0.0.3"]
                 [org.apache.sshd/sshd-core "0.5.0"]
                 [log4j "1.2.16"]
                 [ch.qos.logback/logback-classic "0.9.24"]
                 [commons-io "2.0.1"]
                 [swank-clojure "1.3.3"]])

