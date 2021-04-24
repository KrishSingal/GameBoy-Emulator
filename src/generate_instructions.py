# https://github.com/lmmendes/game-boy-opcodes

import json
import re

operation_map = {}

with open('../opcodes.json') as f:
    data = json.load(f)

def write_op_def(opcode, description, lambda_function, flags_affected, length, cycles):
    return 'operations[{}] = new Operation(\"{}\", {}, {}, {}, {});'.format(opcode, description, lambda_function, flags_affected, length, cycles)

def write_flags_affected(flags):
    return 'new char[] {{\'{}\', \'{}\', \'{}\', \'{}\'}}'.format(flags[0], flags[1], flags[2], flags[3])

def write_cycles(cycles):
    return 'new int[] {{{},{}}}'.format(cycles[0], 0 if len(cycles) == 1 else cycles[1])

# print(data['unprefixed']['0x00']['group'])
# 8-bit ALU op-codes
# print(alu_ops_8bit)

covered_8bit_alu_ops = []

# 8-Bit ALU
alu_ops_8bit = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'x8/alu']
for instruction_data in alu_ops_8bit:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']
    
    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']

    if mnemonic == 'INC':
        lambda_function = '(CPU cpu) -> cpu.INC_8(rf.{})'.format(operand1)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'DEC':
        lambda_function = '(CPU cpu) -> cpu.DEC_8(rf.{})'.format(operand1)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'DAA':
        lambda_function = '(CPU cpu) -> cpu.DAA(rf.{})'.format(operand1)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'SCF':
        lambda_function = '(CPU cpu) -> cpu.SCF()'
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'CCF':
        lambda_function = '(CPU cpu) -> cpu.CCF()'
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'CPL':
        lambda_function = '(CPU cpu) -> cpu.CPL()'
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'ADD':
        lambda_function = '(CPU cpu) -> cpu.ADD_8({}, {}, {}, false)'.format(operand1, 'null' if length == 2 else operand2, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'ADC':
        lambda_function = '(CPU cpu) -> cpu.ADC({}, {}, {}, true)'.format(operand1, 'null' if length == 2 else operand2, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'SUB':
        lambda_function = '(CPU cpu) -> cpu.SUB({}, {}, {}, false, true)'.format(operand1, 'null' if length == 2 else operand2, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'SBC':
        lambda_function = '(CPU cpu) -> cpu.SBC({}, {}, {})'.format(operand1, 'null' if length == 2 else operand2, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'CP':
        lambda_function = '(CPU cpu) -> cpu.CP({}, {}, {})'.format('A', 'null' if operand1 == 'd8' else operand1, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'AND':
        lambda_function = '(CPU cpu) -> cpu.AND({}, {}, {})'.format('A', 'null' if operand1 == 'd8' else operand1, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'OR':
        lambda_function = '(CPU cpu) -> cpu.OR({}, {}, {})'.format('A', 'null' if operand1 == 'd8' else operand1, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
    if mnemonic == 'XOR':
        lambda_function = '(CPU cpu) -> cpu.XOR({}, {}, {})'.format('A', 'null' if operand1 == 'd8' else operand1, 'd8()' if length == 2 else 0)
        covered_8bit_alu_ops.append(opcode)
        
    # hack to make sure we only add operations we've implemented
    if opcode in covered_8bit_alu_ops:
        operation_map[opcode] = write_op_def(opcode, description, lambda_function, flags_affected, length, cycles)

    # Lambda l = ...
    # operations[opcode] = new Operation(description, (CPU cpu) -> cpu.INC_8(rf.operand1), flags_affected, length, cycles)

print('finished 8-bit ALU' if set(covered_8bit_alu_ops) == set([x['addr'] for x in alu_ops_8bit]) else 'not finished 8-bit ALU')
# print(set([x['addr'] for x in alu_ops_8bit]) - set(covered_8bit_alu_ops))




# 16-Bit ALU
alu_ops_16bit = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'x16/alu']
covered_16bit_alu_ops = []

for instruction_data in alu_ops_16bit:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']
    
    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']

    if mnemonic == 'INC':
        lambda_function = '(CPU cpu) -> cpu.INC_16(rf.{})'.format(operand1)
        covered_16bit_alu_ops.append(opcode)
    if mnemonic == 'DEC':
        lambda_function = '(CPU cpu) -> cpu.DEC_16(rf.{})'.format(operand1)
        covered_16bit_alu_ops.append(opcode)
    if mnemonic == 'ADD':
        if operand1 == 'SP':
            lambda_function = '(CPU cpu) -> cpu.ADD_SP({})'.format('r8()')
            covered_16bit_alu_ops.append(opcode)
        else:
            lambda_function = '(CPU cpu) -> cpu.ADD_16(rf.{}, rf.{})'.format(operand1, operand2)
            covered_16bit_alu_ops.append(opcode)
        
        
    # hack to make sure we only add operations we've implemented
    if opcode in covered_16bit_alu_ops:
        operation_map[opcode] = write_op_def(opcode, description, lambda_function, flags_affected, length, cycles)

print('finished 16-bit ALU' if set(covered_16bit_alu_ops) == set([x['addr'] for x in alu_ops_16bit]) else 'not finished 16-bit ALU')
# print(set([x['addr'] for x in alu_ops_8bit]) - set(covered_8bit_alu_ops))






# Misc operations
misc_ops = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'control/misc']
covered_misc_ops = []
for instruction_data in misc_ops:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']
    
    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']

    if mnemonic == 'PREFIX':
        covered_misc_ops.append(opcode)
        continue # don't need to add this to the map
    '''
    if mnemonic == 'NOP':
        lambda_function = '(CPU cpu) -> cpu.NOP()'
        covered_misc_ops.append(opcode)
    if mnemonic == 'STOP':
        lambda_function = '(CPU cpu) -> cpu.STOP()'
        covered_misc_ops.append(opcode)
    if mnemonic == 'HALT':
        lambda_function = '(CPU cpu) -> cpu.HALT()'
        covered_misc_ops.append(opcode)
    if mnemonic == 'DI':
        lambda_function = '(CPU cpu) -> cpu.DI()'
        covered_misc_ops.append(opcode)
    if mnemonic == 'EI':
        lambda_function = '(CPU cpu) -> cpu.EI()'
        covered_misc_ops.append(opcode)
    '''

    lambda_function = '(CPU cpu) -> cpu.{}()'.format(mnemonic)
    covered_misc_ops.append(opcode)

    if opcode in covered_misc_ops:
        operation_map[opcode] = write_op_def(opcode, description, lambda_function, flags_affected, length, cycles)

print('finished misc' if set(covered_misc_ops) == set([x['addr'] for x in misc_ops]) else 'not finished misc')

# 8-Bit Loads

load_ops_8bit = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'x8/lsm']
covered_8bit_load_ops = []

for instruction_data in load_ops_8bit:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']
    
    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']

    '''
    LD_8(Register r1, Register r2, int immediate_value1, int immediate_value2, 
                  boolean operand1_mem, boolean operand2_mem, 
                  boolean operand1_8bit, boolean operand2_8bit, 
                  boolean increment, boolean decrement)
    '''

    r1 = 'null' if (operand1 == 'a8' or operand1 == 'a16') else 'rf.{}'.format(operand1)
    r2 = 'null' if (operand2 == 'd8' or operand2 == 'd16' or operand2 == 'a8' or operand2 == 'a16') else 'rf.{}'.format(operand2)
    immediate_value1 = '{}()'.format(operand1) if (operand1 == 'a8' or operand1 == 'a16') else '-1'
    immediate_value2 = '{}()'.format(operand2) if (operand2 == 'd8' or operand2 == 'd16' or operand2 == 'a8' or operand2 == 'a16') else '-1'
    op1_mem = str(operand1_mem).lower()
    op2_mem = str(operand2_mem).lower()
    operand1_8bit = 'true' if r1 == 'null' and operand1 == 'a8' else 'false'
    operand2_8bit = 'true' if r2 == 'null' and (operand2 == 'd8' or operand2 == 'a8') else 'false'
    increment = str(mnemonic == 'LDH' or '+' in operand1 or '+' in operand2).lower()
    decrement = str('-' in operand1 or '-' in operand2).lower()

    r1 = re.sub('[+-]', '', r1)
    r2 = re.sub('[+-]', '', r2)
    
    lambda_function = '(CPU cpu) -> cpu.LD_8({}, {}, {}, {}, {}, {}, {}, {}, {}, {})'.format(r1, r2, immediate_value1, immediate_value2, op1_mem, op2_mem, operand1_8bit, operand2_8bit, increment, decrement)
    covered_8bit_load_ops.append(opcode)
        
    # hack to make sure we only add operations we've implemented
    if opcode in covered_8bit_load_ops:
        operation_map[opcode] = write_op_def(opcode, description, lambda_function, flags_affected, length, cycles)

print('finished 8-bit loads' if set(covered_8bit_load_ops) == set([x['addr'] for x in load_ops_8bit]) else 'not finished 8-bit loads')



# 16-Bit Loads

load_ops_16bit = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'x16/lsm']
covered_16bit_load_ops = []

for instruction_data in load_ops_16bit:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']
    
    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']

    '''
    LD_16(Register r1, Register r2, int immediate_value1, int immediate_value2, boolean operand1_8bit, boolean operand2_8bit)
    '''
    if mnemonic == 'LD':
        r1 = 'null' if operand1 == 'a16' else 'rf.{}'.format(operand1)
        r2 = 'null' if operand2 == 'd16' or opcode == '0xf8' else 'rf.{}'.format(operand2)
        immediate_value1 = '{}()'.format(operand1) if operand1 == 'a16' else '-1'
        immediate_value2 = '{}()'.format(operand2) if operand2 == 'd16' else '-1'
        operand1_8bit = 'false'
        operand2_8bit = 'true' if opcode == '0xf8' else 'false'
        lambda_function = '(CPU cpu) -> cpu.LD_16({}, {}, {}, {}, {}, {})'.format(r1, r2, immediate_value1, immediate_value2, operand1_8bit, operand2_8bit)
        covered_16bit_load_ops.append(opcode)
    if mnemonic == 'POP':
        lambda_function = '(CPU cpu) -> cpu.POP({})'.format(operand1)
        covered_16bit_load_ops.append(opcode)
    if mnemonic == 'PUSH':
        lambda_function = '(CPU cpu) -> cpu.POP({})'.format(operand1)
        covered_16bit_load_ops.append(opcode)

    if opcode in covered_16bit_load_ops:
        operation_map[opcode] = write_op_def(opcode, description, lambda_function, flags_affected, length, cycles)

print('finished 16-bit loads' if set(covered_16bit_load_ops) == set([x['addr'] for x in load_ops_16bit]) else 'not finished 16-bit loads')



# control flow 
control_ops = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'control/br']
covered_control_ops = []

for instruction_data in control_ops:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']
    
    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']

    '''
    LD_16(Register r1, Register r2, int immediate_value1, int immediate_value2, boolean operand1_8bit, boolean operand2_8bit)
    '''
    if mnemonic == 'JP':
        condition = 'NONE' if operand2 == None else operand1
        lambda_function = '(CPU cpu) -> cpu.JP(rf.{}, {}, Condition.{})'.format('null' if operand2 == 'a16' else operand2, 'a16()' if operand2 == 'a16' else '-1', condition)
        covered_control_ops.append(opcode)
    if mnemonic == 'JR':
        condition = 'NONE' if operand2 == None else operand1
        lambda_function = '(CPU cpu) -> cpu.JP(rf.{}, {}, Condition.{})'.format('null' if operand2 == 'a16' else operand2, 'a16()' if operand2 == 'a16' else '-1', condition)
        covered_control_ops.append(opcode)
    if mnemonic == 'CALL':
        condition = 'NONE' if operand1 == None else operand1
        lambda_function = '(CPU cpu) -> cpu.CALL(Condition.{}, a16())'.format(condition)
        covered_control_ops.append(opcode)
    if mnemonic == 'RET':
        condition = 'NONE' if operand1 == None else operand1
        lambda_function = '(CPU cpu) -> cpu.RET(Condition.{}, false)'.format(condition)
        covered_control_ops.append(opcode)
    if mnemonic == 'RETI':
        lambda_function = '(CPU cpu) -> cpu.RET(Condition.NONE, true)'
        covered_control_ops.append(opcode)
    if mnemonic == 'RST':
        lambda_function = '(CPU cpu) -> cpu.RST(0x{})'.format(operand1[:2])
        covered_control_ops.append(opcode)

    if opcode in covered_control_ops:
        operation_map[opcode] = write_op_def(opcode, description, lambda_function, flags_affected, length, cycles)

print('finished control flow' if set(covered_control_ops) == set([x['addr'] for x in control_ops]) else 'not finished control flow')


# rotations and shifts
unprefixed_bit_ops = [data['unprefixed'][x] for x in data['unprefixed'] if data['unprefixed'][x]['group'] == 'x8/rsb']
covered_unprefixed_bit_ops = []

for instruction_data in unprefixed_bit_ops:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']
    
    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']

    if mnemonic == 'RLCA':
        lambda_function = '(CPU cpu) -> cpu.RLC(rf.A)'
        covered_unprefixed_bit_ops.append(opcode)
    if mnemonic == 'RRCA':
        lambda_function = '(CPU cpu) -> cpu.RRC(rf.A)'
        covered_unprefixed_bit_ops.append(opcode)
    if mnemonic == 'RLA':
        lambda_function = '(CPU cpu) -> cpu.RL(rf.A)'
        covered_unprefixed_bit_ops.append(opcode)
    if mnemonic == 'RRA':
        lambda_function = '(CPU cpu) -> cpu.RR(rf.A)'
        covered_unprefixed_bit_ops.append(opcode)

    if opcode in covered_control_ops:
        operation_map[opcode] = write_op_def(opcode, description, lambda_function, flags_affected, length, cycles)

print('finished unprefixed bit ops' if set(covered_unprefixed_bit_ops) == set([x['addr'] for x in unprefixed_bit_ops]) else 'not finished unprefixed bit ops')


# CB-Prefixed Bit Ops

cbprefixed_bit_ops = [data['cbprefixed'][x] for x in data['cbprefixed'] if data['cbprefixed'][x]['group'] == 'x8/rsb']
covered_cbprefixed_bit_ops = []

for instruction_data in cbprefixed_bit_ops:
    mnemonic = instruction_data['mnemonic']
    opcode = instruction_data['addr']

    description = mnemonic
    operand1 = None
    operand1_mem = False
    operand2 = None
    operand2_mem = False
    if 'operand1' in instruction_data.keys():
        operand1 = instruction_data['operand1']
        description += ' ' + operand1
        operand1_mem = '(' in operand1
        operand1 = re.sub('[()]', '', operand1)
    if 'operand2' in instruction_data.keys():
        operand2 = instruction_data['operand2']
        description += ' ' + operand2
        operand2_mem = ')' in operand2
        operand2 = re.sub('[()]', '', operand2)
    # print(description)

    flags = instruction_data['flags']
    flags_affected = write_flags_affected(flags)

    cycles_data = instruction_data['cycles']
    cycles = write_cycles(cycles_data)

    length = instruction_data['length']
    
    if mnemonic == 'BIT':
        lambda_function = '(CPU cpu) -> cpu.BIT(rf.{}, {})'.format(operand2, operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'SET':
        lambda_function = '(CPU cpu) -> cpu.SET(rf.{}, {})'.format(operand2, operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'SRL':
        lambda_function = '(CPU cpu) -> cpu.SRL(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'SRA':
        lambda_function = '(CPU cpu) -> cpu.SRA(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'SLA':
        lambda_function = '(CPU cpu) -> cpu.SLA(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'SWAP':
        lambda_function = '(CPU cpu) -> cpu.SWAP(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'RLC':
        lambda_function = '(CPU cpu) -> cpu.RLC(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'RRC':
        lambda_function = '(CPU cpu) -> cpu.RRC(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'RR':
        lambda_function = '(CPU cpu) -> cpu.RR(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'RL':
        lambda_function = '(CPU cpu) -> cpu.RL(rf.{})'.format(operand1)
        covered_cbprefixed_bit_ops.append(opcode)
    if mnemonic == 'RES':
        lambda_function = '(CPU cpu) -> cpu.RES(rf.{}, {})'.format(operand2, operand1)
        covered_cbprefixed_bit_ops.append(opcode)

    if opcode in covered_control_ops:
        operation_map[opcode] = write_op_def('0x100+' + opcode, description, lambda_function, flags_affected, length, cycles)

print('finished cbprefixed bit ops' if set(covered_cbprefixed_bit_ops) == set([x['addr'] for x in cbprefixed_bit_ops]) else 'not finished cbprefixed bit ops')


# Final Output

# for operation in sorted(operation_map):
#     print(operation_map[operation])