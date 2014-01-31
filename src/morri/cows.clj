(ns morri.cows
  (:require [morri.cows.devices.bmp085 :as bmp085]
            [morri.cows.devices.sht21 :as sht21]
            [morri.cows.devices.mcp3008 :as mcp3008]
            [morri.cows.conversions :as conv]
            [morri.cows.xively :as xively]
            [ring.util.response :as ring-resp]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str])
  (:gen-class))

(defn tprn [x]
  (prn x)
  x)

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

(defn format-current-value [number-format device-report]
  (update-in device-report [:current_value]
             #(read-string (format number-format %))))

(defn fix-units [preferred-units
                 {:keys [units] :as device-report}]
  (let [convert-to
        (if-let [conv-to
                 (some preferred-units
                       (keys (conv/conversions units)))]
          conv-to
          units)
        {:keys [conv-fn unit-symbol]} (convert-to (units conv/conversions))]
    (-> device-report
        (update-in [:current_value] conv-fn)
        (assoc :unit_symbol unit-symbol))))

(defn read-current-weather [{:keys [devices
                                    preferred-units
                                    number-format]}]
  (let [data-fixer (comp
                    (partial format-current-value number-format)
                    (partial fix-units preferred-units))]
    (map data-fixer (apply concat
            (for [device-cfg devices]
              (case (:name device-cfg)
                :bmp085 (bmp085/read-device device-cfg)
                :sht21 (sht21/read-device device-cfg)
                :mcp3008 (mcp3008/read-device device-cfg)))))))

(defn log-weather [log-state system-state]
  (when (:running log-state)
    (xively/publish-feed
     (:xively log-state)
     (:feed log-state)
     {:version "1.0.0"
      :datastreams (vec (:weather @(:read-weather system-state)))}))
  log-state)

(defn read-log-weather [weather-state system-state]
  (if (:running weather-state)
    (do
      (send-off *agent* read-log-weather system-state)
      (send-off (:log-weather system-state) log-weather system-state)
      (Thread/sleep (:read-log-interval weather-state))
      ;; (println "Reading Weather")
      (let [current-weather (read-current-weather system-state)]
        (assoc weather-state :weather current-weather)))
    weather-state))

(defn format-weather [number-format {:keys [id current_value unit_symbol]}]
  (format (str "%s: " number-format " %s")
          (name id)
          current_value
          (name unit_symbol)))

(defn weather-handler [{:keys [number-format read-weather]}]
  (str/join
   \newline
   (for [report (:weather @read-weather)]
     (format-weather number-format report))))

(defn -main [])

;; We have to do some reorganization because I can't require system
;; here to avoid a circular dependency

;; (defn -main [config-file]
;;   (let [config-file (if config-file config-file system/default-config)
;;         system (system/create-system config-file)]
;;     (start-system system)))
