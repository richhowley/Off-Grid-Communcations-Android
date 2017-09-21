# Off Grid Communications - Android

This Android Studio app is part of an off-grid comminication system that uses Arduinos with HC-12 transceiver modules attached to perfrom communication at a distance without the need of a network.  It is based on a Bluetooth chat example created by Google. 

A smartphone is used to enter and read messages.  It connects to an Arduino via BLE and the Arduino is responsible for sending the message to the other radio.  Although the limit for a single characterisitc write is only 20 bytes, the app allows longer messages to be typed.  When the send button is tapped the app will write the entire message 20 bytes at a time.  Of course, communication between the HC-12 modules is not subject to message length restrictions so the Arduino will write the entire message in one go.

To keep things simple the serial protocal consists only of the rule that all message are followed by an end-of-transmission character (~).  That is how the Arduio getting a message from BLE or HC-12 knows when it has been completely recieved.


