//package de.tum.bgu.msm.models.javascript;
//
//import de.tum.bgu.msm.data.dwelling.DefaultDwellingTypeImpl;
//import de.tum.bgu.msm.models.realEstate.construction.DefaultConstructionDemandStrategy;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//
//import javax.script.ScriptException;
//import java.io.InputStreamReader;
//import java.io.Reader;
//
//public class ConstructionDemandTest {
//    private DefaultConstructionDemandStrategy calculator;
//
//    @Before
//    public void setup() {
//        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("ConstructionDemandCalcMstm"));
//        calculator = new DefaultConstructionDemandStrategy(reader);
//    }
//
//    @Test
//    public void testModelOne() throws ScriptException {
//        Assert.assertEquals(0.00501, calculator.calculateConstructionDemand(0.05, DefaultDwellingTypeImpl.MF234), 0.00001);
//    }
//}
//
//
//
