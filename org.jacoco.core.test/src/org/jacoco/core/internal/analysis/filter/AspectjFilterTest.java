/*******************************************************************************
 * Copyright (c) 2009, 2021 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Lars Grefer - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import org.jacoco.core.internal.instr.InstrSupport;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class AspectjFilterTest implements IFilterOutput {

    private AspectjFilter filter;

    private final FilterContextMock context = new FilterContextMock();

    private Set<AbstractInsnNode> ignored;

    @Before
    public void setUp() {
        filter = new AspectjFilter();
        ignored = new HashSet<AbstractInsnNode>();
    }

    @Test
    public void testAjSyntheticAttribute() {
        final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
                "aspectOf", "()LFoo", null, null);
        m.visitAttribute(new TestAttribute("org.aspectj.weaver.AjSynthetic"));
        m.visitInsn(Opcodes.NOP);
        m.visitInsn(Opcodes.NOP);
        m.visitInsn(Opcodes.NOP);

        filter.filter(m, context, this);

        assertTrue(ignored.containsAll(Arrays.asList(m.instructions.toArray())));
    }

    @Test
    public void testClinitOnlyPre() {
        final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
                "<clinit>", "()V", null, null);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "ajc$preClinit", "()V", false);
        m.visitInsn(Opcodes.NOP);
        m.visitInsn(Opcodes.RETURN);

        filter.filter(m, context, this);

        assertTrue(ignored.containsAll(Arrays.asList(m.instructions.toArray())));
    }

    @Test
    public void testClinitPreAndUserCode() {
        final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
                "<clinit>", "()V", null, null);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "ajc$preClinit", "()V", false);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "myCustomMethod", "()V", false);
        m.visitInsn(Opcodes.RETURN);

        filter.filter(m, context, this);

        assertTrue(ignored.contains(m.instructions.getFirst()));
        assertFalse(ignored.contains(m.instructions.getLast()));
    }

    @Test
    public void testClinitOnlyPost() {
        final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
                "<clinit>", "()V", null, null);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "ajc$postClinit", "()V", false);
        m.visitInsn(Opcodes.NOP);
        m.visitInsn(Opcodes.RETURN);

        filter.filter(m, context, this);

        assertTrue(ignored.containsAll(Arrays.asList(m.instructions.toArray())));
    }

    @Test
    public void testClinitPostAndUserCode() {
        final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
                "<clinit>", "()V", null, null);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "myCustomMethod", "()V", false);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "ajc$postClinit", "()V", false);
        m.visitInsn(Opcodes.RETURN);

        filter.filter(m, context, this);

        assertFalse(ignored.contains(m.instructions.getFirst()));
        assertTrue(ignored.contains(m.instructions.getLast()));
    }

    @Test
    public void testClinitOnlyPreAndPost() {
        final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
                "<clinit>", "()V", null, null);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "ajc$preClinit", "()V", false);
        m.visitInsn(Opcodes.NOP);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "Foo", "ajc$postClinit", "()V", false);
        m.visitInsn(Opcodes.RETURN);

        filter.filter(m, context, this);

        assertTrue(ignored.containsAll(Arrays.asList(m.instructions.toArray())));
    }

    public void ignore(final AbstractInsnNode fromInclusive,
                       final AbstractInsnNode toInclusive) {
        for (AbstractInsnNode i = fromInclusive; i != toInclusive; i = i
                .getNext()) {
            ignored.add(i);
        }
        ignored.add(toInclusive);
    }



    public void merge(final AbstractInsnNode i1, final AbstractInsnNode i2) {
        fail();
    }

    private static class TestAttribute extends Attribute {
        TestAttribute(String type) {
            super(type);
        }
    }
}
