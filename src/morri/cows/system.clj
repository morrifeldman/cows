(ns morri.cows.system
  (:require [morri.cows :as cows]
            [morri.cows.xively :as xively]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [compojure.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [clojurewerkz.machine-head.client :as mh]
            [clojure.pprint :as pp]))

(def default-config
  (io/resource "default-config.edn"))

(defn create-system [config-file]
  (let [config (edn/read-string (slurp config-file))
        log-weather? (:log-weather? config)
        xively (when log-weather?
                 (xively/connect-xively
                  (System/getenv "XIVELY_API_KEY")))]
    {:read-weather (agent
                    {:running true
                     :read-log-interval (:read-log-interval config)})
     :preferred-units (:preferred-units config)
     :number-format (:number-format config)
     :log-weather (agent {:running log-weather?
                          :xively xively
                          :feed (System/getenv "XIVELY_FEED")})
     :serve-weather? (:serve-weather? config)
     :server (atom nil)
     :devices (cows/init-devices config)}))

(defn make-cow-routes [system-state]
  (routes
   (GET "/system" [] (with-out-str (pprint system-state)))
   (GET "/" [] (cows/weather-handler system-state))))

;; based on http://stuartsierra.com/2010/01/08/agents-of-swing

(defn start-system [system-state]
  (when (:serve-weather? system-state)
    (reset! (:server system-state)
            (jetty/run-jetty (make-cow-routes system-state) {:join? false})))
  (send-off (:read-weather system-state) cows/read-log-weather system-state)
  system-state)

(defn stop-agent-fn [state]
  (assoc state :running false))

(defn stop-an-agent [ag]
  (when-let [err (agent-error ag)]
    (println "Agent:")
    (prn @ag)
    (println "Failed with:")
    (println err)
    (restart-agent ag @ag :clear-actions true))
  (send-off ag stop-agent-fn))

(defn stop-system [system-state]
  (when-let [server @(:server system-state)]
    (.stop server))
  (mh/disconnect (:xively @(:log-weather system-state)))
  (doseq [agent-key [:log-weather :read-weather]]
    (stop-an-agent (agent-key system-state)))
  system-state)
