# morri/cows
### What are the cows doing?
A Raspberry Pi weather station written in clojure.

## Usage

Cows must be run with sudo to access the devices, so any environmental
variables you need must be forwarded using visudo `sudo visudo`.  If
you want to use lein, export the LEIN_ROOT environment variable so
that lein doesn't get upset when you run it as root.

For logging to xively, export XIVELY_API_KEY with your xively key.

Clone this repository onto your pi.

Create or edit the configuration cows/resources/default-config.edn.
Currently a bmp085 for temperature and pressure, a sht21 for
temperature and humidity and a ga1a12s202 for light through a mcp3008
a/d converter are available.

### Start up the weather station
#### Preferred method: Remote repl through emacs and the reloaded workflow

Edit lein-cmd changing the ip address to the address assigned to your
pi and the port to whatever you want.  Run `sudo lein deps` to get the
dependencies you will need.  At this point it is best to be running
under tmux or screen. Minimally this means you can do `apt-get install
tmux` and `tmux` to be running under tmux.  Running under tmux will
allow you to disconnect your terminal while a process remains running.
Then run lein-cmd, probably as `./lein-cmd`.  This will use leiningen
to create a headless repl using something like the following command
`sudo lein with-profile +dev repl :headless :host 10.0.1.3 :port
4567`.  Go and get a cup of coffee because this will take a long time,
probably like 5 minutes.  After you see something like `nREPL server
started on port 4567 on host 10.0.1.3`, you are good to go.  Now use
emacs and tramp on a remote computer to edit the file
cows/dev/user.clj via an ssh connection.  At this point it is
convenient to have your pi configured as alias in your remote
computers ~/.ssh/config and copy your public key of to your pi using


## Copyright and License

Copyright © 2013 Morris Feldman, Eclipse Public License
