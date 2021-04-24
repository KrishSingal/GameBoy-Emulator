# https://github.com/lmmendes/game-boy-opcodes

import json

with open('../opcodes.json') as f:
    data = json.load(f)

# print(data['unprefixed']['0x00']['group'])
# 8-bit ALU op-codes
alu_ops_8bit = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'x8/alu']
print(alu_ops_8bit)

