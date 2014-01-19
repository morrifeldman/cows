(ns morri.cows
  (:require [morri.cows.devices.bmp085 :as bmp085]
            [morri.cows.devices.sht21 :as sht21]
            [morri.cows.devices.mcp3008 :as mcp3008]
            [ring.util.response :as ring-resp]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str])
  (:gen-class))

;; Each device gets to take its configuration and add any keys to it
;; that it want durring initialization

(defn init-devices [config]
  (for [device-cfg (:device-cfg config)
        :when (:active device-cfg)
        :let [device-name (:name device-cfg)]]
    (case device-name
      :bmp085 (bmp085/init device-cfg)
      :sht21 (sht21/init device-cfg)
      :mcp3008 (mcp3008/init device-cfg))))

(defn read-current-weather [device-coll]
  (apply concat (for [device-cfg device-coll]
                  (case (:name device-cfg)
                    :bmp085 (bmp085/read-device device-cfg)
                    :sht21 (sht21/read-device device-cfg)
                    :mcp3008 (mcp3008/read-device device-cfg)))))

(defn log-weather [log-state system]
  (when (:running log-state)
    ;; (println "Logging Weather")
    )
  log-state)

(defn read-log-weather [weather-state system]
  (if (:running weather-state)
    (do
      (send-off *agent* read-log-weather system)
      (send-off (:log-weather system) log-weather system)
      (Thread/sleep (:read-log-interval weather-state))
      ;; (println "Reading Weather")
      (assoc weather-state :weather (read-current-weather (:devices system))))
    weather-state))

(defn tprn [x]
  (prn x)
  x)

(defn format-weather [report]
  (format "%s: %.1f %s"
          (name (:content report))
          (:value report)
          (name (:units report))))

(defn handler [system request]
  (-> (ring-resp/response
       (str/join
        \newline
        (for [report (:weather @(:read-weather system))]
          (format-weather report))))
      (ring-resp/content-type "text/plain")))

(defn -main [& args])
