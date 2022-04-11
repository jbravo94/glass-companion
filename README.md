# README

## Introduction
* Start App
* Navigate to Camera
* Open Browser with http://phone-ip:8080
* Single Tap trigger an autofocus
* Swiping zooms forth and back by factor 2
* Double tap activated the LED
* Pinch to zoom, pan to move and tap to autofocus on the previews to interact with the cameras

### Notes
* The http server will be stopped when the preview screen is left, app is minimized or closed
* Only on connection with the browser is possible
Hint: If somebody cannot connect because of a unknown connection left open e.g. tab or other browser,
the best solution is to go back to exit the camera preview, which closes existing connections, reopen the preview and try to reconnect.

## Development
* https://developer.iristick.com/1.3.2/getting-started/development-environment/
* `adb -d tcpip 5555`
* `adb connect 10.0.0.10:5555`

## Camera G1/G2 information
* https://iristick.com/uploads/files/IRI-spec-sheet-Iristick.G2-92021-US-industry_2021-10-27-085217_smgt.pdf
* https://de.wikipedia.org/wiki/Bildaufl%C3%B6sungen_in_der_Digitalfotografie
* Camera 0 16MP 4:3 4619×3464
* Camera 1 5MP 4:3 2582×1936
* Display 428 × 240

## Foundation
* https://developer.iristick.com/1.3.2/iristick-sdk-1.3.2.zip
* https://medium.com/hacktive-devs/creating-a-local-http-server-on-android-49831fbad9ca
* http://www.java2s.com/Code/Jar/h/Downloadhttp221jar.htm
* https://github.com/FriesW/java-mjpeg-streamer/blob/master/Java/src/com/github/friesw/mjpegstreamer/MjpegStreamer.java
* https://github.com/joshdickson/MJPG-Server/blob/master/src/mpegtest/MJPG.java
* http://mjpeg.sanford.io/count.mjpeg

## License
* Code changes are licensed under BSD mentioned in LICENSE.md file (https://opensource.org/licenses/BSD-3-Clause)
