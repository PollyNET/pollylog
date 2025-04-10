(ns pollylogbrowser.core
  (:require
   [reagent.core :as reagent]
   [reagent.dom :as rdom]
  ;  [cljs.core.async :as async]
   [ajax.core :as ajax]
   [clojure.string :as string]
   [cljs.reader :as reader]
   [cuerdas.core :as cstr]
   ;[goog.string :as gstring]
   ;[goog.string.format]
   [antizer.reagent :as ant]
   [cljs-http.client :as http]))
   ;[cljs.core.async :refer [<!]]
   


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce app-state
  (reagent/atom {:ed-visible false
                 :status "none"
                 :error false
                 :dispentry 40
                 :entry-mod {"time" "20121213-0144"
                             "id" 5
                             "operator" ["am"]
                             "changes" ["flashlamps"]
                             "ndfilters" {8 3}
                             "comment" "lorem ipsum"}
                 :entries []
                 :channels {}}))

(def all-changes {"overlap" "Ơ" "windowwipe" "🚿" ;"⚆"
                  "flashlamps" "💡" "pulsepower" "⚡"
                  "restarted" "↻"})

; (def channels {1 "355" 2 "355c" 3 "387" 4 "407"
;                5 "532" 6 "532c" 7 "607" 8 "1064"
;                9 "532NR" 10 "607NR" 11 "355NR" 12 "378NR"
;                13 "532NRc"})

;(defonce PORT 31514)
(defn get-port []
 (if js/goog.DEBUG
   "31514"
   (second (re-find #":(\d+)" js/window.location.host))))

;(defn get-port []
;   "31514")

; (async/go (let [response (async/<! (http/get "http://localhost:8080/entries" {:as :json :with-credentials? false}))]
;             (prn (:status response))
;             (prn (:body response))))

  ;(swap! app-state assoc :all-pieces pcs)

(defn http-error-handler [{:keys [status status-text]}]
  (if-not (get @app-state :error)
  (ant/notification-error {:message "Error during save" :duration 0 :description "Changes will be lost. Is the backend (terminal with .bat) still running?"}))
  (swap! app-state assoc-in [:error] true)
  (swap! app-state assoc-in [:status] "error")
  (.log js/console (str "Error occured: " status " " status-text)))

(defn new-empty-entry "template for a new empty entry" [] 
  ; new approach for unique ids: current unix timestamp
 {"id" (-> (js/moment) (.utc) (.unix) (int))
  "time" (-> (js/moment) (.utc) (.format "YYYYMMDD-HHmm"))
  "operator" [] "changes" [] "ndfilters" {}
  "comment" ""
  "_last_changed" (-> (js/moment) (.utc) (.unix) (int))})


(defn keys-to-int [hashmap]
  (into {} (for [[k v] hashmap] [(int k) v])))

(defn remove-escapes [hashmap]
  (into {} (for [[k v] hashmap] [(string/replace k "\"" "") (if (= k "\"ndfilters\"") (keys-to-int v) v)])))


(defn map-from-vector [v]
  ; (println "map-from-vector " (map keys v))
  (apply hash-map (interleave (map #(get % "id") v) v)))

(defn fetch-channels []
  (ajax/GET (str "http://localhost:" (get-port) "/channelnames")
    {:with-credentials false
     :handler #(swap! app-state assoc-in [:channels] (keys-to-int %))
     :error-handler http-error-handler}))

(fetch-channels)

(defn fetch-handler [data]
  ; (println "data" data)
  (swap! app-state assoc-in [:entries] (map-from-vector (mapv remove-escapes data)))
  (swap! app-state assoc-in [:error] false)
  (swap! app-state assoc-in [:status] "pull finished"))


(defn fetch-entries []
  (swap! app-state assoc-in [:status] "pulling..")
  (ajax/GET (str "http://localhost:" (get-port) "/entries") 
   {:with-credentials false
    :handler fetch-handler :error-handler http-error-handler}))

(fetch-entries)
; regularly fetch entries to detect early when server fails
(js/setInterval fetch-entries (* 45 60 1000))

(defn push-to-backend []
  (swap! app-state assoc-in [:status] "pushing...")
  (ajax/POST (str "http://localhost:" (get-port) "/entries")
   {:params  (clj->js (vals (get @app-state :entries)))
    :handler #(do (js/console.log "post sucessful")(swap! app-state assoc-in [:status] "push finished"))
    :error-handler http-error-handler
    :format :json})
 (js/console.log "pushing to backend"))
 


(defn all-past-operators [state]
  (set (flatten (map #(get % "operator") (vals (get @state :entries))))))


(defn edit-new-entry []
  (js/console.log "swap to mod entry" (clj->js (new-empty-entry)))
  (swap! app-state assoc-in [:entry-mod] (new-empty-entry))
  (swap! app-state assoc-in [:ed-visible] true))

(defn edit-entry-builder [id]
  (fn []
    (swap! app-state assoc-in [:entry-mod] (get-in @app-state [:entries id]))
    (swap! app-state assoc-in [:ed-visible] true)
    (js/console.log "edit id" id)))

(defn del-entry-builder [id]
  (fn []
    (swap! app-state assoc-in [:entries] (dissoc (get @app-state :entries) id))
    (push-to-backend)
   (js/console.log "deleted id" id)))

(defn to-float-od [entry-mod]
  (assoc-in entry-mod ["ndfilters"] (into {} (map (fn [x] [(first x) (cstr/parse-double (second x))]) (get entry-mod "ndfilters"))))
)


(defn save []
  (let [entry-mod (to-float-od (get-in @app-state [:entry-mod]))]
   (swap! app-state assoc-in [:entries (get entry-mod "id")] entry-mod)
   (swap! app-state assoc-in [:entries (get entry-mod "id") "_last_changed"] (-> (js/moment) (.utc) (.unix) (int)))
   (swap! app-state assoc-in [:ed-visible] false)
   (push-to-backend)))
   
(defn check-valid []
 (if (string/includes? (get-in @app-state [:entry-mod "comment"]) ";") 
   (do (ant/notification-error {:message "Error" :description (str "; not valid in comment")}) false) 
   true))

(defn dict-to-string [d]
 (string/join " " (map #(str "<b>" (first %) ":</b> " (second %)) d)))

(defn format-ndfilters [ndsettings]
  (if (> (count (keys ndsettings)) 3)
    (string/join "<br />" (map #(dict-to-string (into {} %)) (partition 3 3 nil (vec ndsettings))))
   (dict-to-string ndsettings)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn entry-in-list 
  "function that returns html for an `entry`"
  []
  (fn [entry]
    ;(js/console.log entry)
    ;(js/console.log "operator " (get entry "operator") (type (get entry "operator")))
    ;(js/console.log "ndfilters " (get-in entry ["ndfilters"]))
    ; (println "id?" (get entry "time") (get entry "id"))
    [:tr
      [:td [ant/button {:on-click (edit-entry-builder (get entry "id")) :size "small" } "✎"] 
       " " [ant/button {:on-click (del-entry-builder (get entry "id")) :size "small" :type "danger"} "x"]]
      [:td (get entry "time")]
      [:td (string/join " " (get entry "operator"))]
      [:td (string/join " " (map (partial get all-changes) (get entry "changes")))]
      [:td {:dangerouslySetInnerHTML {:__html (format-ndfilters (get entry "ndfilters"))}}]
      [:td.comment {:dangerouslySetInnerHTML {:__html (string/replace (get entry "comment") #"\n" "<br />")}}]]))

(defn thead []
 [:tr [:th [ant/button {:on-click edit-new-entry :size "small"} "+"]] 
  [:th "Time"] [:th "Operator"] [:th "Changes"] [:th "ND Filters"] [:th "Comment"]])


(defn sorted-subsetted 
  "" 
  [entries beg end]
  ;(println beg end (min end (count entries)))
  (subvec (vec (sort-by #(get % "time") > (vals entries))) beg (min end (count entries))))
 

(defn list-entries []
  [:div#tablecontainer
   [:table#entry-list
    [:thead [thead]]
    [:tbody
     (doall (for [entry (sorted-subsetted (get-in @app-state [:entries]) 0 (get-in @app-state [:dispentry]))]
              ^{:key (get entry "id")} [entry-in-list entry]))]]])


(defn parse-number-nan [str]
 (if (> (count str) 0)
   (cstr/parse-double str)
   nil))

(defn fix-comma [str]
 (println str)
 (string/replace str "," ".")
)

(defn update-or-remove-nd-filters [path no]
  (fn [value]
    (if value
      (swap! app-state assoc-in (conj path no) value)
      (swap! app-state assoc-in path (dissoc (get-in @app-state path) no)))))


;; table for the nd filters
(defn nd-entry [] 
 (fn [no]
   (if (contains? (get @app-state :channels) no)
     [:td {:style {:padding-right "10px" :text-align "right"}}
      no "   " [:b (get-in @app-state [:channels no])] ":  "
      [ant/input {:size "small" :style {:width "40px" :text-align "right"} 
                  :value (get-in @app-state [:entry-mod "ndfilters" no]) 
                  :onChange #((update-or-remove-nd-filters [:entry-mod "ndfilters"] no) (fix-comma (js->clj (.. % -target -value))))}]]
     [:td ""])))

(defn make-filter-table 
  "make the table with the filter edit fields"
  []
  (fn []
   (let [nchannel (count (get @app-state :channels))]
   ;(if (< (count (get @app-state :channels)) 14)
   (cond
    (< nchannel 14) [:table [:thead] 
      [:tbody
     ;[:tr (for [no (range 1 5)] ^{:key no} (if (contains? (get @app-state :channels) no) [nd-entry no] [:td]))]
       [:tr (for [no (range 1 5)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 5 9)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 9 14)] ^{:key no} [nd-entry no])]]]
    (< nchannel 17) [:table [:thead] 
      [:tbody
       [:tr (for [no (range 1 5)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 5 9)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 9 13)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 13 17)] ^{:key no} [nd-entry no])]]]
    (< nchannel 21) [:table [:thead] 
      [:tbody
       [:tr (for [no (range 1 5)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 5 9)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 9 13)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 13 17)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 17 21)] ^{:key no} [nd-entry no])]]]
    (< nchannel 25) [:table [:thead] 
      [:tbody
       [:tr (for [no (range 1 5)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 5 9)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 9 13)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 13 17)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 17 21)] ^{:key no} [nd-entry no])]
       [:tr (for [no (range 21 25)] ^{:key no} [nd-entry no])]]]
       ))))
     
    
(defn editor 
  "editor for a single entry"
  []
  [:div#editor {:style {:min-width "950px"}} 
   ;[ant/button {:on-click #(swap! app-state update-in [:ed-visible] not)} "toggle ed"]
   ;[ant/button {:on-click push-to-backend} "push"]
   ^{:key (get-in @app-state [:entry-mod "id"])}
    [ant/collapse {:defaultActiveKey "" :activeKey (if (get-in @app-state [:ed-visible]) "1" "") :bordered false}
      [ant/collapse-panel {:header "" :key "1" :showArrow false :disables true}
       [ant/row
        [ant/col {:span 8}
         [:table#editorleft [:thead]
          [:tbody
           [:tr [:td "Time"]
            [:td [ant/date-picker {:format "YYYYMMDD-HHmm" :value (js/moment (get-in @app-state [:entry-mod "time"]) "YYYYMMDD-HHmm")
                                   :on-change (fn [_ d] (swap! app-state assoc-in [:entry-mod "time"] d))
                                   :style {:width "100%"} :show-today false :size "small"
                                   :showTime {:use12Hours false :format "HH:mm"}}]]]
           [:tr [:td "Operator"]
            [ant/tooltip {:title "select from list or type" :placement "right"}
             [:td [ant/select {:mode "tags" :tokenSeparators ["," " "]
                               :value (get-in @app-state [:entry-mod "operator"])
                               :onChange #(swap! app-state assoc-in [:entry-mod "operator"] (js->clj %))
                               :style {:width "200px"}}
                   (for [no (all-past-operators app-state)] ^{:key no} [ant/select-option {:value no} no])]]]]
           [:tr
            [:td {:style {:vertical-align "top"}} "Changes"]
            [:td [ant/checkbox-group {:value (get-in @app-state [:entry-mod "changes"])
                                      :onChange  #(swap! app-state assoc-in [:entry-mod "changes"] (js->clj %))}
                  (for [no (keys all-changes)] ^{:key no} [ant/row [ant/checkbox {:value no} no]])]]]]]]
        [ant/col {:span 16}
         [ant/row [make-filter-table app-state]]
         [:div {:style {:padding "7px"}}]
         [ant/row {:align "top"}
          [ant/col {:span 3} "Comment "]
          [ant/col {:span 21} [ant/input-text-area
                               {:rows 2 :style {:width "100%"}
                              ; use default value else there is a wired rerender
                                :default-value (get-in @app-state [:entry-mod "comment"])
                                :onChange #(swap! app-state assoc-in [:entry-mod "comment"] (js->clj (.. % -target -value)))}]]]
         [:div {:style {:padding "7px"}}]
         [ant/row {:span 3 :offset 21 :type "flex" :justify "end"}
          [ant/button {:type "danger" :on-click #(swap! app-state assoc-in [:ed-visible] false)} "dismiss"]
          [:div {:style {:width "5px"}}]
          [ant/button {:type "primary" :on-click save :disabled (not (check-valid))} "save"]]]]]]])
           
        ;[ant/button {:on-click #(reset! modal1 true)} "list corresp. nodes"]
       

(defn inc-disp "show more entries" []
  ;(swap! a update-in [(keyword id) :counter] inc))
  ;(swap! app-state assoc-in [:dispentry] 300)
  (swap! app-state update-in [:dispentry] #(+ % 50)))

(defn dec-disp "show less entries" []
  (swap! app-state update-in [:dispentry] #(- % 50)))

(defn disp-more 
  "" [] 
  [:div.more 
    ;[:span "less"]
    ;[:span "display older..."]
    [ant/button {:on-click dec-disp 
                 :disabled (> 50 (get @app-state :dispentry))} 
                "less"]
    [:span " " (min (get @app-state :dispentry) (count (get-in @app-state [:entries]))) 
           " out of " (count (get-in @app-state [:entries])) " entries "]
    [ant/button {:on-click inc-disp 
                 :disabled (< (count (get-in @app-state [:entries])) (get @app-state :dispentry))} 
                "more"]])

(defn footer 
  "footer with the link, version and transmit status"
  []
  [:div.footer [:div.footer-left "by martin-rdz  -  visit  " [:a {:href "http://polly.tropos.de"} "polly.tropos.de"] 
                  "  -  " [:a {:href "https://github.com/PollyNET/pollylog"} "pollylog"] " v0.1.7"] 
    [:div.footer-right {:on-click push-to-backend} "status: " (get @app-state :status)]])

(defn page []
  [:div#root {:class (if (get @app-state :error) "error" "fine")}
    [:h1 {:class ["myheader" (if (get @app-state :error) "error" "fine")]} "polly logbook"]
    [editor]
    [list-entries]
    [disp-more]
    ;[:br] @app-state
    [footer]])



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (js/console.log "dev mode")))
    

(defn reload []
  (rdom/render (fn [] [page])
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (reload))
