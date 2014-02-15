(ns morri.cows.devices.sht21
  (:require [morri.cows.devices.i2c :as i2c]
            [morri.cows.binary-utils :as bin]
            [morri.cows.system :as system]))

;; Functions for checksum
(def poly 0x131)

(defn raise-to-poly [b]
  (loop [crc b bit 8]
    (if (= 0 bit) crc
        (if (= 0 (bit-and crc 0x80))
          (recur (bit-shift-left crc 1) (dec bit))
          (recur (bit-xor (bit-shift-left crc 1) poly) (dec bit))))))

(defn checksum? [ba]
  (let [t (raise-to-poly (aget ba 0))
        t (bin/byte->ubyte (raise-to-poly (bit-xor t (aget ba 1))))
        c (bin/byte->ubyte (aget ba 2))]
    (= c t)))                         ; I'm not sure why I need to
                                      ; make these both unsigned but
                                      ; it won't work if I don't

;; Functions to read from sht21
(defn soft-reset [dev]
  (.write dev 0xFE)
  (Thread/sleep 20))

(defn read-sht21 [dev command read-time]
  {:post [(checksum? %)]}
  (let [ba (byte-array 3)]
    (soft-reset dev)
    (.write dev command)
    (Thread/sleep read-time)
    (.read dev ba 0 3)
    ba))

(defn read-14-bit [ba]
  (bin/short->ushort
   (bin/msb+lsb (aget ba 0)
                (bit-and 0xfc (aget ba 1))))) ; mask off the last two lsb

(defn cal-temp-c [s]
  (+ -46.85 (* 175.72 (/ s 65536.0))))

(defn read-sht21-temp [dev]
  (cal-temp-c (read-14-bit (read-sht21 dev 0xF3 85))))

(defn cal-humid [s]
  (+ -6.0 (* 125.0 (/ s 65536.0))))

;; (cal-humid 25424) => 42.5, example from datasheet

(defn read-sht21-humid [dev]
  (cal-humid (read-14-bit (read-sht21 dev 0xF5 29))))

(defmethod system/init-device :sht21
  [{:keys [i2c-bus address] :as cfg}]
  (assoc cfg :sht21 (i2c/connect-i2c i2c-bus address)))

(defmethod system/read-device :sht21
  [{:keys [sht21]}]
  (let [t-celsius (read-sht21-temp sht21)
        humid (read-sht21-humid sht21)]
    [{:id :sht21-temperature
      :current_value t-celsius
      :unit :celsius}
     {:id :sht21-humidity
      :current_value humid
      :unit :percent}]))

;; Some definitions for testing:
(def i2c-bus-number 1)
(def sht21-address 0x40)
(def sht21 (delay (i2c/connect-i2c i2c-bus-number sht21-address)))
;; (read-device {:sht21 @sht21})
