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
            [clojure.set :as set]
            [clojure.java.jdbc :as jdbc]
;            [nrepl.server :refer [start-server stop-server]]
            [clojure.java.io :as io]))

;(defonce repl-server (start-server :port 12223))   
   
(def entries [{"id" 0
               "time" "20190102-1231"
               "operator" ["mr"]
               "changes" ["overlap" "windowcleanin" "flashlamps" "laserhv" "shgthg"]
               "ndfilters" {1 3.2}
               "comment" " dumb_comment"
               "_last_changed" 5}])

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
  ; (println "convert entry" (map type lst) lst)
  (map read-string lst))

(defn keys-to-int [hashmap]
  (into (sorted-map) (for [[k v] hashmap] [(Integer/parseInt k) v])))

;(into (sorted-map) {:a 2 :b 1})
(defn decode-from-json [elem]
  (into {} (for [[k v] elem] [k (if (= k "ndfilters") (keys-to-int v) v)])))

(defn replace-escape [l]
  (string/replace l #"\"" ""))

(defn csv-data->maps [csv-data]
  ; (println "csv-data header" (first csv-data))
  ; (pr "csv-data header" (first csv-data))
  (map zipmap
    (repeat (mapv replace-escape (first csv-data))) ;; First row is the header
    (map convert-entry (rest csv-data))))

(defn load-entries [filename]
  (with-open [reader (io/reader filename)]
    (csv-data->maps (doall (mapv #(string/split % #";") (line-seq reader))))))


(defn get-channelentries [req]
 {:status  200
  :headers {"Content-Type" "application/json"}
  :body    (json/write-str (get (load-config) "channels"))})


; some database stuff
(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     (get (load-config) "dbfilename")
   })

(defn create-db
  "create db and table"
  []
  (println "create the database " db)
  (try (jdbc/db-do-commands db 
          (jdbc/create-table-ddl :logbook
            [;[:timestamp :datetime :default :current_timestamp]
             [:combid :text :unique]
             [:id :int]
             [:time :text]
             [:operator :text]
             [:changes :text]
             [:ndfilters :text]
             [:comment :text]
             [:_last_changed :int]
             [:deleted :int]]))
       (catch Exception e
         (println (.getMessage e)))))

(defn prep-entry-database "doc" [e]
  ;(println (get e "id") (get e "_last_changed"))
  ;(println (str (get e "id") "_" (get e "_last_changed")))
  (assoc e :combid (str (get e "id") "_" (get e "_last_changed")))
)

(defn in? "true if coll contains elm" [coll elm]  
  (some #(= elm %) coll))

(defn write-into-db "doc" [entries]
  (let [db-entr-cids (mapv #(:combid %) (jdbc/query db ["SELECT combid from logbook"]))
        all-entr (mapv prep-entry-database entries)
        new-entr (filter #(not (in? db-entr-cids (% :combid))) all-entr)]
    ;(println "db-entr-cids " db-entr-cids)
    ;(println "new-entr " new-entr)
    ;(jdbc/execute! db ["DELETE FROM fruit WHERE grade < ?" 25.0])
    (jdbc/insert-multi! db :logbook new-entr)
    )
)

(defn mark-deleted-db "doc" [entries]
  (let [db-entr-cids (mapv #(:combid %) (jdbc/query db ["SELECT combid from logbook WHERE deleted IS NULL"]))
        all-entr (mapv prep-entry-database entries)
        ;new-entr (filter #(not (in? db-entr-cids (% :combid))) all-entr)
        current-time (quot (System/currentTimeMillis) 1000)
        del-entr (set/difference (set db-entr-cids) (set (mapv #(:combid %) all-entr)))]
        ;del-entr (set/difference (set db-entr-cids) (set all-entr))]
    ;(println "new-entr " new-entr)
    (println "delete " del-entr)
    ;(jdbc/execute! db ["DELETE FROM fruit WHERE grade < ?" 25.0])
    (doseq [c del-entr]
      (jdbc/update! db :logbook {:deleted current-time} ["combid = ?" c]))
    )
)
; enough of databases 


(defn list-entries [req]
  (pp/pprint req)
  ;(println "load entries" (load-entries logbookfilename))
  (let [entries (load-entries logbookfilename)]
    ;(pr "entries at list" entries)
    (write-into-db entries)
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str entries)}))

(defn update-entries [rep]
  (let [entries (sort-by #(get % "time") #(compare %2 %1) (mapv decode-from-json (:body rep)))]
    (print "!!! update entries")
    (pp/pprint entries)
    (save-entries logbookfilename entries)
    (write-into-db entries)
    (mark-deleted-db entries))
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
   (println (get config "dbfilename") (.exists (io/file (get config "dbfilename")))
   (if-not (.exists (io/file (get config "dbfilename"))) (create-db))
   (server/run-server app {:port port})
   (println (str "Running webserver at http://127.0.0.1:" port "/"))
   (browse/browse-url (str "http://localhost:" port)))))

(comment
 (use 'pollylog.core :reload))
