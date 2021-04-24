import struct

with open('../roms/gb_bios.bin', 'rb') as file:
    content = file.read()

for data in struct.unpack("i", content[:4]):
    print(data)