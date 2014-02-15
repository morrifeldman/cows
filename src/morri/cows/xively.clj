(ns morri.cows.xively
  (:require [clojurewerkz.machine-head.client :as mh]
            [clojure.data.json :as json]
            [clojurewerkz.machine-head.durability :as durable]))

(defn connect-xively [api-key]
  (mh/connect
   "tcp://api.xively.com:1883"
   (mh/generate-id)
   (durable/new-memory-persister)
   {:username api-key}))

(defn publish-feed [conn feed data]
  (let [topic (format "/v2/feeds/%s.json" feed)]
    (mh/publish conn topic (json/write-str data) 0)))

;; Below here are just for experimentation

(def xively-api-key (System/getenv "XIVELY_API_KEY"))
(def xively-feed 2070333582)
(def payload {:version "1.0.0"
              :datastreams
              [{:id :test
                :unit {:symbol "C"
                       :label "Celsius"}}]})

(def test-datastream-topic
  (format "/v2/feeds/%s/datastreams/%s.json"
          xively-feed "test"))

(def test-payload {:unit {:symbol "C"
                          :label "Celsius"}})

;; (def conn (connect-xively xively-api-key))
;; (publish-feed conn xively-feed payload)
;; (mh/publish conn test-datastream-topic (json/write-str test-payload) 0)
;
;

;; (mh/disconnect conn)
