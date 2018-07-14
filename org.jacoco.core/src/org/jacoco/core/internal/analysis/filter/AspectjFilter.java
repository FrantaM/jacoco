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

import org.objectweb.asm.Attribute;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.regex.Pattern;

/**
 * Filters synthetic methods created by the AspectJ-Compiler (ajc)
 */
public class AspectjFilter implements IFilter {

    private static final String AJ_SYNTHETIC_ATTRIBUTE = "org.aspectj.weaver.AjSynthetic";
    private static final Pattern AJC_CLOSURE_PATTERN = Pattern.compile(".*\\$AjcClosure\\d+");

    public void filter(MethodNode methodNode, IFilterContext context, IFilterOutput output) {

        if (context.getSuperClassName().equals("org/aspectj/runtime/internal/AroundClosure")
                && AJC_CLOSURE_PATTERN.matcher(context.getClassName()).matches()) {
            output.ignore(methodNode.instructions.getFirst(), methodNode.instructions.getLast());
            return;
        }

        if (isAjSynthetic(methodNode)) {
            output.ignore(methodNode.instructions.getFirst(), methodNode.instructions.getLast());
            return;
        }

        if (methodNode.name.equals("<clinit>")) {
            checkStaticInitializer(methodNode, output);
        }

    }

    /**
     * This method finds calls to ajc$preClinit() and ajc$postClinit() and ignores them.
     *
     * @param methodNode The {@literal <clinit>()}-Method
     * @param output
     */
    private void checkStaticInitializer(MethodNode methodNode, IFilterOutput output) {
        MethodInsnNode preClinitNode = null;
        MethodInsnNode postClinitNode = null;

        for (AbstractInsnNode node = methodNode.instructions.getFirst();
             node != null;
             node = node.getNext()) {

            if (node.getOpcode() != Opcodes.INVOKESTATIC) {
                continue;
            }
            String name = ((MethodInsnNode) node).name;

            if (name.equals("ajc$preClinit")) {
                preClinitNode = (MethodInsnNode) node;
            }

            if (name.equals("ajc$postClinit")) {
                postClinitNode = (MethodInsnNode) node;
            }
        }

        if (preClinitNode != null) {
            ignorePreClinitCall(methodNode, output, preClinitNode);
        }

        if (postClinitNode != null) {
            ignorePostClinitCall(methodNode, output, postClinitNode);
        }

        if (preClinitNode != null && postClinitNode != null && getNextRealOp(preClinitNode) == postClinitNode) {
            output.ignore(preClinitNode, postClinitNode);
        }
    }

    private void ignorePreClinitCall(MethodNode methodNode, IFilterOutput output, MethodInsnNode preClinitNode) {
        AbstractInsnNode from = preClinitNode;
        AbstractInsnNode to = preClinitNode;

        if (from != methodNode.instructions.getFirst() && isEffectivelyFirst(preClinitNode.getPrevious())) {
            from = methodNode.instructions.getFirst();
        }
        if (isEffectivelyLast(preClinitNode.getNext())) {
            to = methodNode.instructions.getLast();
        }

        output.ignore(from, to);
    }

    private void ignorePostClinitCall(MethodNode methodNode, IFilterOutput output, MethodInsnNode postClinitNode) {
        AbstractInsnNode from = postClinitNode;
        AbstractInsnNode to = postClinitNode;

        if (postClinitNode.getNext().getOpcode() == Opcodes.GOTO) {
            JumpInsnNode next = (JumpInsnNode) postClinitNode.getNext();
            to = next.label;
        }

        if (isEffectivelyFirst(postClinitNode.getPrevious())) {
            from = methodNode.instructions.getFirst();
        }
        if (isEffectivelyLast(to.getNext())) {
            to = methodNode.instructions.getLast();
        }

        output.ignore(from, to);
    }

    private AbstractInsnNode getNextRealOp(AbstractInsnNode preClinitNode) {
        AbstractInsnNode next = preClinitNode.getNext();

        if (next != null && next.getOpcode() <= 0) {
            return getNextRealOp(next);
        } else {
            return next;
        }
    }

    private boolean isAjSynthetic(MethodNode methodNode) {
        if (methodNode.attrs != null) {
            for (Attribute attr : methodNode.attrs) {
                if (attr.type.equals(AJ_SYNTHETIC_ATTRIBUTE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isEffectivelyFirst(AbstractInsnNode insnNode) {
        if (insnNode.getPrevious() == null) {
            return true;
        } else if (insnNode.getOpcode() <= 0) {
            return isEffectivelyFirst(insnNode.getPrevious());
        } else {
            return false;
        }
    }

    private boolean isEffectivelyLast(AbstractInsnNode insnNode) {
        if (insnNode.getNext() == null) {
            return true;
        } else if (insnNode.getOpcode() <= 0) {
            return isEffectivelyLast(insnNode.getNext());
        } else {
            return false;
        }
    }
}
