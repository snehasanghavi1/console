/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.hal.processor;

import com.google.auto.service.AutoService;
import org.jboss.auto.AbstractProcessor;
import org.jboss.hal.spi.AsyncColumn;
import org.jboss.hal.spi.Column;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.jboss.hal.processor.TemplateNames.*;

/**
 * Processor which automates registration of finder columns marked with either {@code @Column} or {@code @AsyncColumn}.
 *
 * @author Harald Pehl
 */
@AutoService(Processor.class)
@SuppressWarnings("HardCodedStringLiteral")
@SupportedAnnotationTypes({"org.jboss.hal.spi.Column", "org.jboss.hal.spi.AsyncColumn"})
public class ColumnRegistrationProcessor extends AbstractProcessor {

    private static final String COLUMN_INIT_TEMPLATE = "ColumnInit.ftl";
    private static final String COLUMN_INIT_PACKAGE = "org.jboss.hal.client.finder";
    private static final String COLUMN_INIT_CLASS = "ColumnInit";

    private static final String COLUMN_MODULE_TEMPLATE = "ColumnModule.ftl";
    private static final String COLUMN_MODULE_PACKAGE = COLUMN_INIT_PACKAGE;
    private static final String COLUMN_MODULE_CLASS = "ColumnModule";

    private final Set<ColumnInfo> columnInfos;

    public ColumnRegistrationProcessor() {
        super(ColumnRegistrationProcessor.class, TEMPLATES);
        columnInfos = new LinkedHashSet<>(); // do not change to HashSet since we need a stable iteration order!
    }

    @Override
    protected boolean onProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element e : roundEnv.getElementsAnnotatedWith(AsyncColumn.class)) {
            ColumnInfo columnInfo = columnInfo((TypeElement) e, true);
            columnInfos.add(columnInfo);
            debug("Discovered async finder column [%s]", columnInfo);
        }

        for (Element e : roundEnv.getElementsAnnotatedWith(Column.class)) {
            ColumnInfo columnInfo = columnInfo((TypeElement) e, false);
            columnInfos.add(columnInfo);
            debug("Discovered finder column [%s]", columnInfo);
        }

        // Don't generate files in onLastRound, since the generated GIN module
        // needs to be picked up by the GinModuleProcessor!
        if (!columnInfos.isEmpty()) {
            debug("Generating code for column init");
            code(COLUMN_INIT_TEMPLATE, COLUMN_INIT_PACKAGE, COLUMN_INIT_CLASS,
                    () -> {
                        Map<String, Object> context = new HashMap<>();
                        context.put(GENERATED_WITH, ColumnRegistrationProcessor.class.getName());
                        context.put(PACKAGE_NAME, COLUMN_INIT_PACKAGE);
                        context.put(CLASS_NAME, COLUMN_INIT_CLASS);
                        context.put("columnInfos", columnInfos);
                        return context;
                    });

            debug("Generating code for column module");
            code(COLUMN_MODULE_TEMPLATE, COLUMN_MODULE_PACKAGE, COLUMN_MODULE_CLASS,
                    () -> {
                        Map<String, Object> context = new HashMap<>();
                        context.put(GENERATED_WITH, ColumnRegistrationProcessor.class.getName());
                        context.put(PACKAGE_NAME, COLUMN_MODULE_PACKAGE);
                        context.put(CLASS_NAME, COLUMN_MODULE_CLASS);
                        context.put("columnInitClassName", COLUMN_INIT_CLASS);
                        return context;
                    });

            info("Successfully generated column initialization class [%s], [%s].",
                    COLUMN_INIT_CLASS, COLUMN_MODULE_CLASS);
            columnInfos.clear();
        }
        return false;
    }

    private ColumnInfo columnInfo(TypeElement element, boolean async) {
        PackageElement packageElement = (PackageElement) element.getEnclosingElement();
        String columnClass = element.getQualifiedName().toString();
        String id;
        if (async) {
            AsyncColumn asyncColumn = element.getAnnotation(AsyncColumn.class);
            id = asyncColumn.value();
        } else {
            Column asyncColumn = element.getAnnotation(Column.class);
            id = asyncColumn.value();
        }
        return new ColumnInfo(columnClass, id, async);
    }


    public static final class ColumnInfo {

        private final String fqClassName;
        private final String id;
        private final boolean async;

        public ColumnInfo(final String fqClassName, final String id, final boolean async) {
            this.fqClassName = fqClassName;
            this.id = id;
            this.async = async;
        }

        @Override
        @SuppressWarnings("SimplifiableIfStatement")
        public boolean equals(final Object o) {
            if (this == o) { return true; }
            if (!(o instanceof ColumnInfo)) { return false; }

            ColumnInfo that = (ColumnInfo) o;

            if (async != that.async) { return false; }
            return fqClassName.equals(that.fqClassName);

        }

        @Override
        public int hashCode() {
            int result = fqClassName.hashCode();
            result = 31 * result + (async ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return fqClassName;
        }

        public String getFqClassName() {
            return fqClassName;
        }

        public String getId() {
            return id;
        }

        public boolean isAsync() {
            return async;
        }
    }
}
