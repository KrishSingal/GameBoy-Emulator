import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CPUTest {

    @Test
    public void flagsTest() {
        RegisterFile rf = new RegisterFile();

        System.out.println(RegisterFile.CFLAG_BIT);
        System.out.println(RegisterFile.NFLAG_BIT);
        System.out.println(RegisterFile.HFLAG_BIT);
        System.out.println(RegisterFile.ZFLAG_BIT);

        rf.setFlag(RegisterFile.CFLAG_BIT, true);
        rf.setFlag(RegisterFile.NFLAG_BIT, true);
        rf.setFlag(RegisterFile.HFLAG_BIT, true);
        rf.setFlag(RegisterFile.ZFLAG_BIT, true);
        assertEquals(rf.getFlag(RegisterFile.CFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.NFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.HFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.ZFLAG_BIT), 1);

        rf.setFlag(RegisterFile.CFLAG_BIT, false);
        assertEquals(rf.getFlag(RegisterFile.CFLAG_BIT), 0);
        assertEquals(rf.getFlag(RegisterFile.NFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.HFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.ZFLAG_BIT), 1);

        rf.setFlag(RegisterFile.ZFLAG_BIT, false);
        assertEquals(rf.getFlag(RegisterFile.CFLAG_BIT), 0);
        assertEquals(rf.getFlag(RegisterFile.NFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.HFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.ZFLAG_BIT), 0);
    }
}