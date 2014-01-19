(ns morri.cows.devices.i2c
  (:import (com.pi4j.io.i2c I2CFactory)))

(defn connect-i2c [bus-number device-address]
  (.. I2CFactory (getInstance bus-number) (getDevice device-address)))
