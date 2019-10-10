/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.resource.Requirement;

public class RequirementParserTest {

    @Test
    public void testParseRequireCapability() {
        String[] requirements = {
                "osgi.extender; filter:=\"(&(osgi.extender=osgi.ds)(version>=1.0))\"; effective:=active",
                "osgi.contract;\n        osgi.contract=JavaJAXRS;"
        };

        ResolverImpl resolver = new ResolverImpl();

        Requirement actual0 = resolver.parseRequement(requirements[0]);
        Assert.assertEquals("(&(osgi.extender=osgi.ds)(version>=1.0))", actual0.getDirectives().get("filter"));
        Assert.assertEquals("active", actual0.getDirectives().get("effective"));

        Requirement actual1 = resolver.parseRequement(requirements[1]);
        Assert.assertEquals("JavaJAXRS", actual1.getAttributes().get("osgi.contract"));
    }

}
