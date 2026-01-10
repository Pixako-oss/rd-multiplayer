# rd-multiplayer
Work in progress...

### Protocol
```
Break a block (C2S+S2C)
BLOCK_BREAK x,y,z

Place a block (C2S+S2C)
BLOCK_PLACE x,y,z

Request level (C2S)
LEVEL

Send level (S2C)
LEVEL <level.dat>

Keepalive (C2S+S2C)
KEEPALIVE <number>
```