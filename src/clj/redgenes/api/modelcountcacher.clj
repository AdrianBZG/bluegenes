(ns redgenes.api.modelcountcacher
  (:require [clj-http.client :as client]
            [org.httpkit.client :as http]
            [clojure.string :refer [trim-newline]]
            [redgenes.whitelist :as config]
            [redgenes.mines :as mines]
            [redgenes.redis :refer [wcar*]]
            [taoensso.carmine :as car :refer (wcar)]
))

(defn store-response [item mine thecount]
  (wcar* (car/hset (str "modelcount-" mine) item thecount))
  (println mine "(response: " item thecount ")")
)

(defn count-query
  "Builds the query for a count of a given path"
  [path]
    (str "<query model=\"genomic\" view=\"" path ".id\" ></query>")
  )

(defn get-count
"asynchronously loads the count for a given path"
  [item mine-name mine-url]
  (http/post (str "http://" mine-url "/service/query/results")
    {:form-params
     {:format "count"
      :query (count-query (name item))}}
    (fn [{:keys [status headers body error]}] ;; asynchronous response handling
      (if error
        (println "Failed, exception is " error) ;;Oh noes :(
        (store-response item mine-name (trim-newline body)) ;;success - store the stuff!
))))

(defn load-model [mine-name]
  (let [mine-url (get-in mines/mines [(keyword mine-name) :mine :service :root])
        model (client/get
               (str "http://" mine-url "/service/model?format=json")
               {:keywordize-keys? true :as :json})
        whitelisted-model (select-keys (:classes (:model (:body model))) config/whitelist)
        promises (doall (map #(get-count % mine-name mine-url) (keys whitelisted-model)))
        ]
    "ok"
))

(defn load-all-models
  "Loads all the model counts in the known mines.cljc file"
  []
  (let [promises
    (doall (map
      (fn [[mine-name _]]
        (println "\n loading details for" mine-name)
        (load-model (name mine-name)) "OK..") mines/mines))]
"Loading"))
