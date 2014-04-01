# morri/cows
### What are the cows doing?
A Raspberry Pi weather station written in clojure.

## Usage

Cows must be run with sudo to access the devices, so any environmental
variables you need must be forwarded using visudo `sudo visudo`.  If
you want to use lein, export the LEIN_ROOT environment variable so
that lein doesn't get upset when you run it as root.

For logging to xively, export XIVELY_API_KEY with your xively key and
pass it on to sudo with `sudo visudo`

Clone this repository onto your pi.

Create or edit the configuration cows/resources/default-config.edn.
Currently a bmp085 for temperature and pressure, a sht21 for
temperature and humidity and a ga1a12s202 for light through a mcp3008
a/d converter are available.

### Start up the weather station

First run `sudo lein deps` to download all the dependencies and go get
a cup of coffee.

Make sure you are running under tmux or screen, so that you can detach
after starting a repl.

`apt-get install tmux`

`tmux`

#### Start up a repl and activate the weather station

Run the following command to start a repl.  Go get a cup of coffee
because it will take about 5 minutes.

`sudo lein with-profile +dev repl`

Once the repl is started execute `(reset)` in the user namespace to
start the weather station.

## Copyright and License

Copyright © 2013 Morris Feldman, Eclipse Public License
