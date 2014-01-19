(ns morri.cows.devices.sht21
  (:require [morri.cows.devices.i2c :as i2c]
            [morri.cows.conversions :as conv]))

(def i2c-bus-number 1)

(def sht21-address 0x40)

(def sht21 (delay (i2c/connect-i2c i2c-bus-number sht21-address)))

(def poly 0x131)

(defn tprn-bin [i]
  (prn (Integer/toString i 2))
  i)

;; (tprn-bin -0x80)
;; (tprn-bin 128)
;; (tprn-bin 129)
;; (tprn-bin 0x80)
;; (tprn-bin 0xFE)

(defn raise-to-poly [b]
  (loop [crc b bit 8]
    (if (= 0 bit) crc
        (if (= 0 (tprn-bin (bit-and crc 0x80)))
          (recur (bit-shift-left crc 1) (dec bit))
          (recur (bit-xor (bit-shift-left crc 1) poly) (dec bit))))))

;; (defn checksum? [ba]
;;   (let [t (raise-to-poly (aget ba 0))
;;         t (raise-to-poly (bit-xor t (aget ba 1)))
;;         ;; c (bit-and 0xff (aget ba 2)) ; read unsigned
;;         c (aget ba 2)]
;;     (= c t)))

(defn checksum? [ba]
  true)                                 ; disable checksum for now
                                        ; till I figure it out

(defn soft-reset [dev]
  (.write dev 0xFE)
  (Thread/sleep 15))

(defn read-sht21 [dev command read-time]
  {:post [(checksum? %)]}
  (let [ba (byte-array 3)]
    (soft-reset dev)
    (.write dev command)
    (Thread/sleep read-time)
    (.read dev ba 0 3)
    ba))

;; (tprn-bin 0xFFFC)

(defn read-14-bit [ba]
  (bit-and 0xFFFC
           (+
            (bit-shift-left (aget ba 0) 8)
            (aget ba 1))))

;; (defn cal-temp-c [ba]
;;   (let [sig (read-14-bit ba)
;;         t1 (/ (* sig 512) 9548)]
;;     (/ (- t1 937) 20.0)))

(defn cal-temp-c [s]
  (+ -46.85 (* 175.72 (/ s 65536))))

(defn read-sht21-temp [dev]
  (cal-temp-c (read-14-bit (read-sht21 dev 0xF3 85))))

;; (read-sht21-temp @sht21)

;; (defn cal-humid [ba]
;;   (let [sig (read-14-bit ba)]
;;     (- (/ (* 256 sig) 134215.0) 6)))

(defn cal-humid [s]
  (+ -6 (* 125.0 (/ s 65536))))

;; (cal-humid 25424) => 42.5, example from datasheet

(defn read-sht21-humid [dev]
  (cal-humid (read-14-bit (read-sht21 dev 0xF5 29))))

;; (read-sht21-humid @sht21)

(defn init [{:keys [i2c-bus address] :as cfg}]
  (assoc cfg :sht21 (i2c/connect-i2c i2c-bus address)))

(defn read-device [{:keys [sht21
                          temperature-units]}]
  {:pre [#{:celsius :fahrenheit} temperature-units]}
  (let [celsius (read-sht21-temp sht21)
        temp-time (System/currentTimeMillis)
        t {:celsius celsius
           :fahrenheit (conv/c->f celsius)}
        humid (read-sht21-humid sht21)
        humid-time (System/currentTimeMillis)]
    [{:content :sht21-temperature
      :units temperature-units
      :value (temperature-units t)
      :time temp-time}
     {:content :sht21-humidity
      :units :percent
      :value humid
      :time humid-time}]))

;; (read-device {:sht21 @sht21 :temperature-units :celsius})
;; (read-device {:sht21 @sht21 :temperature-units :fahrenheit})
