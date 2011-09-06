(ns clojars.web.search
  (:use clojars.web.common
        clojars.db
        hiccup.core
        hiccup.page-helpers
        hiccup.form-helpers
        clojure.contrib.json
))

(defn format-date [s]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") s))

(defn- jar->json [jar]
  (let [m {:jar_name (:jar_name jar)
           :group_name (:group_name jar)
           :version (:version jar)
           :description (:description jar)}
        created (:created jar)]
    (if created
      (assoc m :created created)
      m)))

(defn json-gen [query]
  (let [results (filter #(not (nil? (:jar_name %)))
                        (search-jars query))]
    (json-str {:count (count results)
               :results (map jar->json results)})))

(defn json-search [query]
  {:status 200,
   :headers {"Content-Type" "application/json; charset=UTF-8"}
   :body (json-gen query)})

(defn html-search [account query]
  (html-doc account (str (h query) " - search")
    [:h1 "Search for " (h query)]
    [:ul
     (for [jar (search-jars query)
           ;; bc too lazy to see why blank entries are showing up
           :when (not (nil? (:jar_name jar)))]
       [:li.search-results
        (jar-link jar) " " (h (:version jar))
        [:br]
        (let [description (:description jar)]
          (when (and (not= "" description)
                     (not (nil? description)))
            [:span.desc (h description)
             [:br]]))
        [:span.details
         (user-link (:username jar))
         " "
         (if-let [created (:created jar)]
           [:td (format-date created)])]])]))

(defn search [account params]
  (let [q (params "q")]
    (if (= (params "format") "json")
      (json-search q)
      (html-search account q))))
