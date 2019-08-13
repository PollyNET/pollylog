(ns pollylog.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [clojure.java.browse :as browse]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :as resp]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.string :as string]
            [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))
   
   
(def entries [{"id" 0
               "time" "20190102-1231"
               "operator" ["mr"]
               "changes" ["overlap" "windowcleanin" "flashlamps" "laserhv" "shgthg"]
               "ndfilters" {1 3.2}
               "comment" " "}])

(defn load-config []
 (json/read-str (slurp "config.json")))

(println (load-config))

(def logbookfilename (get (load-config) "logbookfilename"))
;(def logbookfilename "pollylog_production/logbook_example.csv")
;(def logbookfilename "testlogbook.csv")

              
(defn list-values [ks dict]
  (println ks dict)
  (vec (map #(get dict %) ks))) 

(defn parse-list-to-vec [list]
 (let [ks (vec (keys (first list)))
       rows (mapv (partial list-values ks) list)]
   (println "write " (concat [ks] rows))
   (concat [ks] rows)))


(defn save-entries [filename entries]
  (with-open [writer (io/writer filename)]
    (doseq [line (parse-list-to-vec entries)]
      (println "output" (map type line) line)
      (println "output" (map pr-str line))
      (.write writer (string/join ";" (map pr-str line)))
      (.newLine writer))))

;(save-entries "../testlogbook.csv" entries)

(defn convert-entry [lst]
  (println "convert entry")
  (println (map type lst) lst)
  (map read-string lst))

(defn keys-to-int [hashmap]
  (into (sorted-map) (for [[k v] hashmap] [(Integer/parseInt k) v])))

;(into (sorted-map) {:a 2 :b 1})
(defn decode-from-json [elem]
  (into {} (for [[k v] elem] [k (if (= k "ndfilters") (keys-to-int v) v)])))


(defn csv-data->maps [csv-data]
  (map zipmap
    (repeat (first csv-data)) ;; First row is the header
    (map convert-entry (rest csv-data))))


(defn load-entries [filename]
  (with-open [reader (io/reader filename)]
    (csv-data->maps (doall (mapv #(string/split % #";") (line-seq reader))))))


(defn get-channelentries [req]
 {:status  200
  :headers {"Content-Type" "application/json"}
  :body    (json/write-str (get (load-config) "channels"))})

(defn list-entries [req]
  (pp/pprint req)
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str (load-entries logbookfilename))})


(defn update-entries [rep]
  (let [entries (sort-by #(get % "time") #(compare %2 %1) (mapv decode-from-json (:body rep)))]
    (pp/pprint entries)
    (save-entries logbookfilename entries))
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str {"sucess" true})})

(defroutes app-routes
  (route/resources "/")
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (GET "/entries" [] list-entries)
  (POST "/entries" [] update-entries)
  (GET "/channelnames" [] get-channelentries)
  (route/not-found "route not found"))

(def app (wrap-cors (wrap-json-body app-routes)
                    :access-control-allow-origin #".+"
                    :access-control-allow-methods [:get :put :post :delete]))


(defn -main 
  "This is our app's entry point"
  [& args]
  (let [config (load-config)
        port (Integer/parseInt (or (str (get config "port")) "31514"))] 
   (server/run-server app {:port port})
   (println (str "Running webserver at http:/127.0.0.1:" port "/"))
   (browse/browse-url (str "http://localhost:" port))))
  
(+ 5 2)

(comment
 (use 'pollylog.core :reload))
