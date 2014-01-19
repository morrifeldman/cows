(ns morri.cows.devices.bmp085
  (:require [clojure.math.numeric-tower :as math]
            [morri.cows.devices.i2c :as i2c]
            [morri.cows.conversions :as conv])
  (:import (java.nio ByteBuffer)))

(defn next-short! [bb]
  (.getShort bb))

(defn short->ushort [s]
  (bit-and s 0xffff)); promotes to int and masks off bits
; (short->ushort -1) ; => 65535

(defn next-ushort! [bb]
  (short->ushort (next-short! bb)))

(defn read-into-byte-buffer [device address len]
  (let [offset 0
        ba (byte-array len)
        n-read (.read device address ba offset len)]
    (ByteBuffer/wrap ba)))

(def cal-setup
  [[:ac1 :short]
   [:ac2 :short]
   [:ac3 :short]
   [:ac4 :ushort]
   [:ac5 :ushort]
   [:ac6 :ushort]
   [:b1 :short]
   [:b2 :short]
   [:mb :short]
   [:mc :short]
   [:md :short]])

(defn read-bmp085-cal [bmp085]
  {:post [(not-any? #{0 0xFFFF} (vals %))]}
  (let [len (* 2 (count cal-setup))
        bb (read-into-byte-buffer bmp085 0xAA len)]
    (into {}
          (for [[param precision] cal-setup
                :let [param-val (case precision
                                  :short (next-short! bb)
                                  :ushort (next-ushort! bb))]]
            [param param-val]))))

(defn init [{:keys [i2c-bus address] :as cfg}]
  (let [bmp085 (i2c/connect-i2c i2c-bus address)
        bmp085-cal (read-bmp085-cal bmp085)]
    (assoc cfg :bmp085 bmp085 :cal bmp085-cal)))

(defn read-short [device address]
  (let [bb (read-into-byte-buffer device address 2)]
    (next-short! bb)))

(defn read-ushort [device address]
  (short->ushort (read-short device address)))

;; read uncompesated temp.
(defn read-ut [bmp085]
  (do (.write bmp085 0xF4 0x2E)
      (Thread/sleep 5)
      (read-ushort bmp085 0xF6)))

;; see the BMP085 datasheet for the temperature and pressure calibration formulas
;; divide by 2^n is the same as bit shift right by n
;; multiply by 2^n is the same as bit shift left by n

(defn compute-b5
  "Compute b5 based on the calibration cal and the uncompensated temp
  ut.  b5 is used in the calculation of both calibrated temp and
  calibrated pressure"
  [cal ut]
  (let [{:keys [ac6 ac5 mc md]} cal
        x1 (bit-shift-right (* (- ut ac6) ac5) 15)
        x2 (/ (bit-shift-left mc 11) (+ x1 md))
        b5 (int (+ x1 x2))]
    b5))

(defn cal-temp
  "Calculate calibrated temperature in C based on the calibration cal
  and the uncalibrated temp ut"
  [cal ut]
  (let [b5 (compute-b5 cal ut)
        t (bit-shift-right (+ b5 8) 4)]
    (/ t 10.0)))


;; Now let's read the pressure
;; write 0x34+(oss<<6) into reg 0xF4, wait
;; oss means how much oversampling
;; oss of 2 means high res

;; read uncompesated temp.
;; From datasheet:
;; (current-temp-f)
(def ms-sleep-time
  {0 4.5
   1 7.5
   2 13.5
   3 25.5})

(defn read-up
  "Read uncalibrated pressure given an oversampling rate oss of 0 1 2
  or 3"
  [bmp085 oss]
  (do
    (.write bmp085 0xF4 (+ 0x34 (bit-shift-left oss 6)))
    (Thread/sleep (ms-sleep-time oss))
    (let [msb (.read bmp085 0xf6)
          lsb (.read bmp085 0xf7)
          xlsb (.read bmp085 0xf8)]
      (bit-shift-right
       (+ (bit-shift-left msb 16)
          (bit-shift-left lsb 8)
          xlsb)
       (- 8 oss)))))

(defn sq [x] (* x x))

(defn cal-pressure
  "Calibrate the pressure give a calibration cal, oversampling setting
  oss, uncalibrated temperature ut and uncalibrated pressure up"
  [cal oss ut up]
  (let [{:keys [b5 b2 ac2 ac1 ac3 b1 ac4]} cal
        b5 (compute-b5 cal ut)
        b6 (- b5 4000)
        x1 (bit-shift-right (* b2 (bit-shift-right (sq b6) 12)) 11)
        x2 (bit-shift-right (* ac2 b6) 11)
        x3 (+ x1 x2)
        b3 (int (/ (+ (bit-shift-left (+ (* ac1 4) x3) oss) 2) 4))
        x1 (bit-shift-right (* ac3 b6) 13)
        x2 (bit-shift-right (* b1 (bit-shift-right (* b6 b6) 12)) 16)
        x3 (bit-shift-right (+ (+ x1 x2) 2) 2)
        b4 (bit-shift-right (* ac4 (+ x3 32768)) 15)
        b7 (* (- up b3) (bit-shift-right 50000 oss))
        p (int (if (< b6 0x80000000) (/ (* b7 2) b4) (* (/ b7 b4) 2)))
        x1 (sq (bit-shift-right p 8))
        x1 (bit-shift-right (* x1 3038) 16)
        x2 (bit-shift-right (* -7357 p) 16)
        p (+ p (bit-shift-right (+ x1 x2 3791) 4))]
    (/ p 100.0)))


(defn sea-level-pressure [altitude p]
  (/ p (Math/pow (- 1 (/ altitude 44330)) 5.255)))

(defn read-device [{:keys [bmp085
                           cal
                           oss
                           altitude
                           temperature-units
                           pressure-units]}]
  {:pre [(#{:celsius :fahrenheit} temperature-units)
         (#{:mmHg :hPa} pressure-units)]}
  (let [ut (read-ut bmp085)
        temp-time (System/currentTimeMillis)
        up (read-up bmp085 oss)
        pressure-time (System/currentTimeMillis)
        celsius (cal-temp cal ut)
        uncomp-hPa (cal-pressure cal oss ut up)
        sea-level-hPa (sea-level-pressure altitude uncomp-hPa)
        t {:celsius celsius
           :fahrenheit (conv/c->f celsius)}
        p {:hPa sea-level-hPa
           :mmHg (conv/hPa->mmHg sea-level-hPa)}]
    [{:content :bmp085-temperature
      :units temperature-units
      :value (temperature-units t)
      :time temp-time}
     {:content :bmp085-pressure
      :units pressure-units
      :value (pressure-units p)
      :time pressure-time}]))

;; extra/experimentation functions

(def i2c-bus-number 1)
;; 0 for rev. 1 RasPi board, 1 for rev. 2 RasPi board.

(def bmp085-address 0x77)

(def bmp085 (delay (i2c/connect-i2c i2c-bus-number bmp085-address)))

(def bmp085-cal (delay (read-bmp085-cal @bmp085)))

(def my-altitude 67.480)

(defn t&p []
  (let [oss 1
        ut (read-ut @bmp085)
        up (read-up @bmp085 oss)
        ct (cal-temp @bmp085-cal ut)
        uncomp-hPa (cal-pressure @bmp085-cal oss ut up)
        hPa-sea-level (sea-level-pressure my-altitude uncomp-hPa)
        mmHg-sea-level (conv/hPa->mmHg hPa-sea-level)]
    (prn (format "Current temp is %.2f C or %.2f F" ct (conv/c->f ct)))
    (prn (format "Current Pressure is %.1f hPa/mbar or %.1f mmHg"
                 hPa-sea-level mmHg-sea-level))))

;; (t&p)

(def test-config
  (delay {:bmp085 @bmp085
          :cal @bmp085-cal
          :oss 1
          :altitude 0}))

;; (read-device (assoc @test-config :temperature-units :celsius :pressure-units :mmHg))
;; (read-device (assoc @test-config :temperature-units :fahrenheit :pressure-units :mmHg))
;; (read-device (assoc @test-config :temperature-units :celsius :pressure-units :hPa))
