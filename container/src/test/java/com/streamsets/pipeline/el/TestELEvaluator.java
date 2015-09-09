/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.el;

import com.streamsets.pipeline.api.ElConstant;
import com.streamsets.pipeline.api.ElFunction;
import com.streamsets.pipeline.api.el.ELEval;
import com.streamsets.pipeline.api.el.ELEvalException;
import com.streamsets.pipeline.api.el.ELVars;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestELEvaluator {

  @Test
  public void testElFunction() throws ELEvalException {
    ELEval elEval = new ELEvaluator("testElFunction", ValidTestEl.class);
    ELVars variables = elEval.createVariables();
    Boolean result = elEval.eval(variables, "${location:city() eq CITY}", Boolean.class);
    Assert.assertTrue(result);
  }

  @Test
  public void testElFunctionMetadata() {
    ELEval elEval = new ELEvaluator("testElFunctionMetadata", ValidTestEl.class);

    Assert.assertEquals(elEval.getConfigName(), "testElFunctionMetadata");

    List<ElFunctionDefinition> elFunctionDefinitions = ((ELEvaluator) elEval).getElFunctionDefinitions();
    Assert.assertEquals(1, elFunctionDefinitions.size());

    ElFunctionDefinition cityDef = elFunctionDefinitions.get(0);
    Assert.assertEquals("location:city", cityDef.getName());
    Assert.assertEquals("location", cityDef.getGroup());
    Assert.assertEquals("Returns the Address", cityDef.getDescription());
    Assert.assertEquals("String", cityDef.getReturnType());
  }

  @Test(expected = RuntimeException.class)
  public void testNonStaticFunctionEl() {
    new ELEvaluator("testNonStaticFunctionEl", NonStaticFunctionEl.class);
  }

  @Test(expected = RuntimeException.class)
  public void testEmptyNameFunctionEl() {
    new ELEvaluator("testEmptyNameFunctionEl", EmptyNameFunctionEl.class);
  }

  @Test
  public void testElConstant() throws ELEvalException {
    ELEval elEval = new ELEvaluator("testElConstant", ValidTestEl.class);
    ELVars variables = elEval.createVariables();
    Boolean result = elEval.eval(variables, "${CITY eq \"San Francisco\"}", Boolean.class);
    Assert.assertTrue(result);
  }

  @Test
  public void testElConstantMetadata() {
    ELEval elEval = new ELEvaluator("testElConstantMetadata", ValidTestEl.class);
    List<ElConstantDefinition> elConstantDefinitions = ((ELEvaluator) elEval).getElConstantDefinitions();

    Assert.assertEquals(1, elConstantDefinitions.size());

    ElConstantDefinition constDef = elConstantDefinitions.get(0);
    Assert.assertEquals("CITY", constDef.getName());
    Assert.assertEquals("Declares the CITY constant to be 'San Francisco'", constDef.getDescription());
    Assert.assertEquals("String", constDef.getReturnType());
  }

  @Test(expected = RuntimeException.class)
  public void testNonStaticConstEl() {
    new ELEvaluator("testNonStaticConstEl", NonStaticConstEl.class);
  }

  @Test(expected = RuntimeException.class)
  public void testEmptyNameConstEl() {
    new ELEvaluator("testEmptyNameConstEl", EmptyNameConstEl.class);
  }

  @Test
  public void testParseEL() throws ELEvalException {
    //valid EL
    ELEvaluator.parseEL("${location:city() eq CITY}");

    //Invalid EL
    try {
      ELEvaluator.parseEL("${location:city() eq }");
      Assert.fail("ELEvalException expected as the EL string is not valid");
    } catch (ELEvalException e) {

    }
  }

  public static class ValidTestEl {

    @ElConstant(name = "CITY", description = "Declares the CITY constant to be 'San Francisco'")
    public static final String CITY = "San Francisco";

    @ElFunction(prefix = "location", name = "city", description = "Returns the Address")
    public static String getCity() {
      return "San Francisco";
    }

  }

  public static class NonStaticConstEl {
    @ElConstant(name = "CITY", description = "Declares the CITY constant to be 'San Francisco'")
    public final String CITY = "San Francisco";
  }

  public static class NonPublicConstEl {
    @ElConstant(name = "CITY", description = "Declares the CITY constant to be 'San Francisco'")
    static final String CITY = "San Francisco";
  }

  public static class EmptyNameConstEl {
    @ElConstant(name = "", description = "Declares the CITY constant to be 'San Francisco'")
    public static final String CITY = "San Francisco";
  }

  public static class NonStaticFunctionEl {
    @ElFunction(prefix = "location", name = "city", description = "Returns the Address")
    public String getCity() {
      return "San Francisco";
    }
  }

  public static class NonPublicFunctionEl {
    @ElFunction(prefix = "location", name = "city", description = "Returns the Address")
    static String getCity() {
      return "San Francisco";
    }
  }

  public static class EmptyNameFunctionEl {
    @ElFunction(prefix = "location", name = "", description = "Returns the Address")
    public static String getCity() {
      return "San Francisco";
    }
  }
}