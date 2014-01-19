(ns morri.cows.devices.mcp3008
  (:require [clojure.java.io :as io])
  (:import (com.pi4j.wiringpi Spi))
  (:import (java.nio ByteBuffer)))

(defn start-spi [channel speed]
  {:post [(not (= % -1))]}
  (Spi/wiringPiSPISetup channel speed))

;; (start-spi 0 500000)

(defn init [{:keys [speed spi-channel] :as cfg}]
  (assoc cfg :spi (start-spi speed spi-channel)))

(def test-cfg {:name :mcp3008
               :spi-channel 0
               :speed 500000
               :ad-channels {:ch0 {:name :ga1a12s202}}})

;; (init test-cfg)

(def channel-cfg
  {:ch0 -0x80                           ; Single Ended
   :ch1 -0x90                           ; Neg. because no uint in java
   :ch2 -0xA0
   :ch3 -0xB0
   :ch4 -0xC0
   :ch5 -0xD0
   :ch6 -0xE0
   :ch7 -0xF0
   :ch0-ch1 0x00                        ; Differential ch0+ ch1-
   :ch1-ch0 0x10                        ; Differential ch1+ ch0-
   :ch2-ch3 0x20
   :ch3-ch2 0x30
   :ch4-ch5 0x40
   :ch5-ch4 0x50
   :ch6-ch7 0x60
   :ch7-ch6 0x70})

(defn byte->ubyte [b]
  (bit-and 0xff b))

(defn msb+lsb [msb lsb]
  (bit-or (bit-shift-left msb 8) lsb))

;; First Byte is "00000001" =  1
;; Second Byte is "10000000" = -128,  for Single ended Ch0
;; Third Byte doesn't matter

;; Receive:
;; First byte doesn't matter
;; Second byte, bit 6 0, bits 7-8 MSB
;; Third byte LSB

(defn read-mcp3008
  "Assumes we already called start-spi on the spi-channel"
  [spi-channel ad-channel]
  {:pre [(contains? channel-cfg ad-channel)]}
  (let [ba (byte-array 3)]
    (aset-byte ba 0 1)
    (aset-byte ba 1 (ad-channel channel-cfg))
    (aset-byte ba 2 0)
    (Spi/wiringPiSPIDataRW spi-channel ba 3)
    (let [msb (bit-and 0x03 (aget ba 1)) ; Only keep the last two bits
          lsb (byte->ubyte (aget ba 2))] ; Read as unsigned
      (msb+lsb msb lsb))))

(defn read-ra1a12s202 [spi-channel ad-channel]
  (let [raw-range 1023
        log-range 5                     ;1024 = 10^5 lux
        log-lux (/ (* (read-mcp3008 spi-channel ad-channel)
                      log-range) raw-range)]
    (Math/pow 10 log-lux)))

(defn read-device [{:keys [spi-channel ad-channels]}]
  (for [[chan-key {:keys [name] :as chan-cfg}] ad-channels]
    (case name
      :ga1a12s202
      {:content :ga1a12s202-light-level
       :units :lux
       :value (read-ra1a12s202 spi-channel chan-key)
       :time (System/currentTimeMillis)})))

;; (init test-cfg)
;; (read-device (init test-cfg))
