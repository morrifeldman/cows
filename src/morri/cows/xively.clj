(ns morri.cows.xively
  (:require [clojurewerkz.machine-head.client :as mh]
            [clojure.data.json :as json]
            [clojurewerkz.machine-head.durability :as durable]
            [morri.cows.conversions :as conv]))

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
(def xively-feed (System/getenv "XIVELY_FEED"))

(def payload {:version "1.0.0"
              :datastreams
              [{:id :test
                :current_value 25.0
                :unit "Celsius"
                :unit_symbol conv/celsius-symbol}]})

;; (def conn (connect-xively xively-api-key))
;; (publish-feed conn xively-feed payload)

;; (mh/disconnect conn)
