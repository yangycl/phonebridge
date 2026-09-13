# PhoneBridge protocol v1

Transport: TCP. Each frame:

```
uint32be json_length
utf-8 json
if the json has "data_len" > 0:
    exactly data_len raw bytes follow
```

Client speaks first after connect.

## hello

```json
{"op":"hello","ver":1,"pin":"1234"}
```

```json
{"op":"hello_ok","ver":1,"mode":"ro","caps":["list","stat","read"],"root":"/storage/emulated/0","device":"Pixel"}
```

`mode` is `ro` or `rw`. Write ops must fail when `ro`.

## list / stat / read

```json
{"op":"list","id":1,"path":"."}
{"op":"ok","id":1,"entries":[{"name":"DCIM","dir":true,"size":0,"mtime":0}]}
```

```json
{"op":"stat","id":2,"path":"DCIM/a.jpg"}
{"op":"ok","id":2,"dir":false,"size":1234,"mtime":1710000000}
```

```json
{"op":"read","id":3,"path":"DCIM/a.jpg","offset":0,"length":4096}
{"op":"ok","id":3,"data_len":4096}
<4096 bytes>
```

## reserved write ops

```json
{"op":"write","id":4,"path":"x.bin","offset":0,"data_len":100}
<100 bytes>
{"op":"create","id":5,"path":"x.bin"}
{"op":"delete","id":6,"path":"x.bin"}
{"op":"rename","id":7,"path":"a","to":"b"}
```

v0 servers return `{"op":"err","id":4,"code":"ro"}` unless `mode` is `rw` and `write` is in `caps`.

Paths are relative to the exported root. `.` is the root. `..` is rejected.
