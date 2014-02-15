(ns morri.cows
  (:require [morri.cows.system :as system]
            [morri.cows.devices [bmp085 mcp3008 sht21]])
  (:gen-class))

(defn -main [& cfg-file-input]
  (let [config-file (if (first cfg-file-input)
                      (first cfg-file-input)
                      system/default-config)
        system-state (system/create-system config-file)]
    (system/start-system system-state)))
