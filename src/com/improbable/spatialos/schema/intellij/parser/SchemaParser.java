package com.improbable.spatialos.schema.intellij.parser;

import com.improbable.spatialos.schema.intellij.SchemaLanguage;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SchemaParser implements PsiParser {
    public static final SchemaParser SCHEMA_PARSER = new SchemaParser();

    public static final String KEYWORD_PACKAGE = "package";
    public static final String KEYWORD_IMPORT = "import";
    public static final String KEYWORD_ENUM = "enum";
    public static final String KEYWORD_TYPE = "type";
    public static final String KEYWORD_COMPONENT = "component";
    public static final String KEYWORD_OPTION = "option";
    public static final String KEYWORD_ID = "id";
    public static final String KEYWORD_DATA = "data";
    public static final String KEYWORD_EVENT = "event";
    public static final String KEYWORD_COMMAND = "command";
    public static final String KEYWORD_ANNOTATION_START = "[";

    public static final IFileElementType SCHEMA_FILE = new IFileElementType(SchemaLanguage.SCHEMA_LANGUAGE);

    public static final IElementType KEYWORD = new Node("Keyword");
    public static final IElementType DEFINITION_NAME = new Node("Definition Name");

    public static final IElementType PACKAGE_DEFINITION = new Node("Package Definition");
    public static final IElementType PACKAGE_NAME = new Node("Package Name");

    public static final IElementType IMPORT_DEFINITION = new Node("Import Definition");
    public static final IElementType IMPORT_FILENAME = new Node("Import Filename");

    public static final IElementType OPTION_DEFINITION = new Node("Option Definition");
    public static final IElementType OPTION_NAME = new Node("Option Name");
    public static final IElementType OPTION_VALUE = new Node("Option Value");

    public static final IElementType TYPE_NAME = new Node("Type Name");
    public static final IElementType TYPE_PARAMETER_NAME = new Node("Type Parameter Name");

    public static final IElementType FIELD_TYPE = new Node("Field Type");
    public static final IElementType FIELD_NAME = new Node("Field Name");
    public static final IElementType FIELD_NUMBER = new Node("Field Number");

    public static final IElementType ENUM_DEFINITION = new Node("Enum Definition");
    public static final IElementType ENUM_VALUE_DEFINITION = new Node("Enum Value Definition");

    public static final IElementType DATA_DEFINITION = new Node("Data Definition");
    public static final IElementType FIELD_DEFINITION = new Node("Field Definition");
    public static final IElementType EVENT_DEFINITION = new Node("Event Definition");

    public static final IElementType TYPE_DEFINITION = new Node("Type Definition");
    public static final IElementType COMPONENT_DEFINITION = new Node("Component Definition");
    public static final IElementType COMPONENT_ID_DEFINITION = new Node("Component ID Definition");

    public static final IElementType COMMAND_DEFINITION = new Node("Command Definition");
    public static final IElementType COMMAND_NAME = new Node("Command Name");
    public static final IElementType ANNOTATION = new Node("Annotation Definition");
    public static final IElementType ANNOTATION_FIELD = new Node("Annotation Field");
    public static final IElementType ANNOTATION_FIELD_ARRAY = new Node("Annotation Field Array");

    public static final Pattern OPTION_PATTERN = Pattern.compile("(?i)(?:\\d+\\.?\\d*|true|false|\"[^\"]*\"?|_)");

    private static class Node extends IElementType {
        public Node(String debugName) {
            super(debugName, SchemaLanguage.SCHEMA_LANGUAGE);
        }
    }

    public static class RangedNode extends Node {

        public final List<RangedNodeEntry> entries = new ArrayList<>();

        public RangedNode(String debugName) {
            super(debugName);
        }

        public RangedNode addEntry(int from, int to, TextAttributesKey attributes) {
            entries.add(new RangedNodeEntry(from, to, attributes));
            return this;
        }
    }

    public static class RangedNodeEntry {
        public final int from;
        public final int to;
        public final TextAttributesKey attributes;

        private RangedNodeEntry(int from, int to, TextAttributesKey attributes) {
            this.from = from;
            this.to = to;
            this.attributes = attributes;
        }
    }

    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        Instance instance = new Instance(builder);
        instance.parseSchemaFile(root);
        return builder.getTreeBuilt();
    }

    private static class Instance {
        private PsiBuilder builder;
        private enum Construct {
            STATEMENT,
            BRACES,
            TOP_LEVEL,
        }

        public Instance(@NotNull PsiBuilder builder) {
            this.builder = builder;
        }

        private void error(@Nullable PsiBuilder.Marker marker, IElementType elementType, Construct construct,
                           String s, Object... args) {
            if (marker != null) {
                marker.done(elementType);
            }
            String errorMessage = String.format(s, args);
            PsiBuilder.Marker errorMarker = builder.mark();

            while (builder.getTokenType() != null && !builder.eof()) {
                if ((construct == Construct.STATEMENT || construct == Construct.TOP_LEVEL) &&
                    isToken(SchemaLexer.SEMICOLON)) {
                    errorMarker.error(errorMessage);
                    builder.advanceLexer();
                    return;
                }
                if ((construct == Construct.BRACES || construct == Construct.TOP_LEVEL) &&
                    isToken(SchemaLexer.RBRACE)) {
                    errorMarker.error(errorMessage);
                    builder.advanceLexer();
                    return;
                }
                if (construct == Construct.STATEMENT && isToken(SchemaLexer.RBRACE)) {
                    errorMarker.error(errorMessage);
                    return;
                }
                builder.advanceLexer();
            }
            errorMarker.error(errorMessage);
        }

        private String getTokenText() {
            return builder.getTokenText() == null ? "<EOF>" : builder.getTokenText();
        }

        private String getIdentifier() {
            return builder.getTokenText() == null ? "" : builder.getTokenText();
        }

        private int getInteger() {
            if (builder.getTokenText() == null) {
                return 0;
            }
            try {
                return Integer.parseInt(builder.getTokenText());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private String getString() {
            String text = builder.getTokenText();
            return text == null ? "" : text.substring(1, text.length() - 2);
        }

        private boolean isToken(IElementType token) {
            return builder.getTokenType() == token;
        }

        private boolean isIdentifier(@NotNull String identifier) {
            return builder.getTokenType() == SchemaLexer.IDENTIFIER &&
                    builder.getTokenText() != null && builder.getTokenText().equals(identifier);
        }

        private void consumeTokenAs(@Nullable IElementType nodeType) {
            PsiBuilder.Marker marker = nodeType == null ? null : builder.mark();
            builder.advanceLexer();
            if (marker != null) {
                marker.done(nodeType);
            }
        }

        private void parsePackageDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, PACKAGE_DEFINITION, Construct.STATEMENT,
                      "Expected a package name after '%s'.", KEYWORD_PACKAGE);
                return;
            }
            consumeTokenAs(PACKAGE_NAME);
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, PACKAGE_DEFINITION, Construct.STATEMENT,
                      "Expected ';' after %s definition.", KEYWORD_PACKAGE);
                return;
            }
            consumeTokenAs(null);
            marker.done(PACKAGE_DEFINITION);
        }

        private void parseImportDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.STRING)) {
                error(marker, IMPORT_DEFINITION, Construct.STATEMENT,
                      "Expected a quoted filename after '%s'.", KEYWORD_IMPORT);
                return;
            }
            String filename = getString();
            consumeTokenAs(IMPORT_FILENAME);
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, IMPORT_DEFINITION, Construct.STATEMENT,
                      "Expected ';' after '%s \"%s\"'.", KEYWORD_IMPORT, filename);
                return;
            }
            consumeTokenAs(null);
            marker.done(IMPORT_DEFINITION);
        }

        private void parseOptionDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, OPTION_DEFINITION, Construct.STATEMENT,
                      "Expected identifier after '%s'.", KEYWORD_OPTION);
                return;
            }
            String name = getIdentifier();
            consumeTokenAs(OPTION_NAME);
            if (!isToken(SchemaLexer.EQUALS)) {
                error(marker, OPTION_DEFINITION, Construct.STATEMENT,
                      "Expected '=' after '%s %s'.", KEYWORD_OPTION, name);
                return;
            }
            consumeTokenAs(null);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, OPTION_DEFINITION, Construct.STATEMENT,
                      "Expected option value after '%s %s = '.", KEYWORD_OPTION, name);
                return;
            }
            String value = getIdentifier();
            consumeTokenAs(OPTION_VALUE);
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, OPTION_DEFINITION, Construct.STATEMENT,
                      "Expected ';' after '%s %s = %s'.", KEYWORD_OPTION, name, value);
                return;
            }
            consumeTokenAs(null);
            marker.done(OPTION_DEFINITION);
        }

        private @Nullable String parseTypeName(@NotNull PsiBuilder.Marker marker) {
            PsiBuilder.Marker typeMarker = builder.mark();
            String name = getIdentifier();
            consumeTokenAs(TYPE_NAME);
            if (!isToken(SchemaLexer.LANGLE)) {
                typeMarker.done(FIELD_TYPE);
                return name;
            }
            name = name + '<';
            consumeTokenAs(null);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                typeMarker.drop();
                error(marker, FIELD_DEFINITION, Construct.STATEMENT, "Expected typename after '%s'.", name);
                return null;
            }
            name = name + getIdentifier();
            consumeTokenAs(TYPE_PARAMETER_NAME);
            while (true) {
                if (isToken(SchemaLexer.RANGLE)) {
                    name = name + '>';
                    consumeTokenAs(null);
                    typeMarker.done(FIELD_TYPE);
                    return name;
                }
                if (isToken(SchemaLexer.COMMA)) {
                    name = name + ", ";
                    consumeTokenAs(null);
                    if (!isToken(SchemaLexer.IDENTIFIER)) {
                        typeMarker.drop();
                        error(marker, FIELD_DEFINITION, Construct.STATEMENT, "Expected typename after ','.");
                        return null;
                    }
                    name = name + getIdentifier();
                    consumeTokenAs(TYPE_PARAMETER_NAME);
                    continue;
                }
                typeMarker.drop();
                error(marker, FIELD_DEFINITION, Construct.STATEMENT, "Invalid '%s' inside <>.", getTokenText());
                return null;
            }
        }

        private void parseFieldDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            String typeName = parseTypeName(marker);
            if (typeName == null) {
                return;
            }
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT, "Expected field name after '%s'.", typeName);
                return;
            }
            String fieldName = getIdentifier();
            consumeTokenAs(FIELD_NAME);
            if (!isToken(SchemaLexer.EQUALS)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT,
                      "Expected '=' after '%s %s'.", typeName, fieldName);
                return;
            }
            consumeTokenAs(null);
            if (!isToken(SchemaLexer.INTEGER)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT,
                      "Expected field number after '%s %s = '.", typeName, fieldName);
                return;
            }
            int fieldNumber = getInteger();
            consumeTokenAs(FIELD_NUMBER);
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT,
                      "Expected ';' after '%s %s = %d'.", typeName, fieldName, fieldNumber);
                return;
            }
            consumeTokenAs(null);
            marker.done(FIELD_DEFINITION);
        }

        private void parseEnumContents() {
            while (isToken(SchemaLexer.IDENTIFIER)) {
                PsiBuilder.Marker marker = builder.mark();
                String name = getIdentifier();
                consumeTokenAs(FIELD_NAME);
                if (!isToken(SchemaLexer.EQUALS)) {
                    error(marker, ENUM_VALUE_DEFINITION, Construct.STATEMENT, "Expected '=' after '%s'.", name);
                    continue;
                }
                consumeTokenAs(null);
                if (!isToken(SchemaLexer.INTEGER)) {
                    error(marker, ENUM_VALUE_DEFINITION, Construct.STATEMENT,
                          "Expected integer enum value after '%s = '.", name);
                    continue;
                }
                int value = getInteger();
                consumeTokenAs(FIELD_NUMBER);
                if (!isToken(SchemaLexer.SEMICOLON)) {
                    error(marker, ENUM_VALUE_DEFINITION, Construct.STATEMENT,
                          "Expected ';' after '%s = %d'.", name, value);
                    continue;
                }
                consumeTokenAs(null);
                marker.done(ENUM_VALUE_DEFINITION);
            }
        }

        private void parseTypeContents() {
            while (true) {
                if (isIdentifier(KEYWORD_OPTION)) {
                    PsiBuilder.Marker marker = builder.mark();
                    builder.advanceLexer();
                    boolean lookaheadIsOption = !isToken(SchemaLexer.LANGLE);
                    marker.rollbackTo();
                    if (lookaheadIsOption) {
                        parseOptionDefinition();
                        continue;
                    }
                }
                if (isIdentifier(KEYWORD_ENUM)) {
                    parseEnumDefinition();
                    continue;
                }
                if (isIdentifier(KEYWORD_TYPE)) {
                    parseTypeDefinition();
                    continue;
                }
                if (isToken(SchemaLexer.IDENTIFIER)) {
                    parseFieldDefinition();
                    continue;
                }
                return;
            }
        }

        private void parseComponentIdDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.EQUALS)) {
                error(marker, COMPONENT_ID_DEFINITION, Construct.STATEMENT,
                      "Expected '=' after '%s'.", KEYWORD_ID);
                return;
            }
            consumeTokenAs(null);
            if (!isToken(SchemaLexer.INTEGER)) {
                error(marker, COMPONENT_ID_DEFINITION, Construct.STATEMENT,
                      "Expected integer ID value after '%s = '.", KEYWORD_ID);
                return;
            }
            int value = getInteger();
            consumeTokenAs(FIELD_NUMBER);
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, COMPONENT_ID_DEFINITION, Construct.STATEMENT,
                      "Expected ';' after '%s = %d'.", KEYWORD_ID, value);
                return;
            }
            consumeTokenAs(null);
            marker.done(COMPONENT_ID_DEFINITION);
        }

        private void parseDataDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, DATA_DEFINITION, Construct.STATEMENT, "Expected typename after '%s'.", KEYWORD_DATA);
                return;
            }
            String typeName = parseTypeName(marker);
            if (typeName == null) {
                return;
            }
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, DATA_DEFINITION, Construct.STATEMENT,
                        "Expected ';' after '%s %s'.", KEYWORD_DATA, typeName);
                return;
            }
            consumeTokenAs(null);
            marker.done(DATA_DEFINITION);
        }

        private void parseEventDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, EVENT_DEFINITION, Construct.STATEMENT, "Expected typename after '%s'.", KEYWORD_EVENT);
                return;
            }
            String typeName = parseTypeName(marker);
            if (typeName == null) {
                return;
            }
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, EVENT_DEFINITION, Construct.STATEMENT,
                      "Expected field name after '%s %s'.", KEYWORD_EVENT, typeName);
                return;
            }
            String fieldName = getIdentifier();
            consumeTokenAs(FIELD_NAME);
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, EVENT_DEFINITION, Construct.STATEMENT,
                        "Expected ';' after '%s %s %s'.", KEYWORD_EVENT, typeName, fieldName);
                return;
            }
            consumeTokenAs(null);
            marker.done(EVENT_DEFINITION);
        }

        private void parseComponentContents() {
            while (true) {
                if (isIdentifier(KEYWORD_OPTION)) {
                    PsiBuilder.Marker marker = builder.mark();
                    builder.advanceLexer();
                    boolean lookaheadIsOption = !isToken(SchemaLexer.LANGLE);
                    marker.rollbackTo();
                    if (lookaheadIsOption) {
                        parseOptionDefinition();
                        continue;
                    }
                }
                if (isIdentifier(KEYWORD_ID)) {
                    parseComponentIdDefinition();
                    continue;
                }
                if (isIdentifier(KEYWORD_DATA)) {
                    parseDataDefinition();
                    continue;
                }
                if (isIdentifier(KEYWORD_EVENT)) {
                    parseEventDefinition();
                    continue;
                }
                if(isIdentifier(KEYWORD_COMMAND)) {
                    parseCommandDefinition();
                    continue;
                }
                if (isToken(SchemaLexer.IDENTIFIER)) {
                    parseFieldDefinition();
                    continue;
                }
                return;
            }
        }

        private void parseAnnotation() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(null);

            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, ANNOTATION, Construct.STATEMENT, "Expected type after '['.");
                return;
            }
            consumeTokenAs(TYPE_NAME);

            if(isToken(SchemaLexer.LPARENTHESES)) { //If the annotation has fields
                if(builder.lookAhead(2) == SchemaLexer.EQUALS) { //fully-qualified names
                    consumeTokenAs(null);
                    while(true) {
                        if (!isToken(SchemaLexer.IDENTIFIER)) {
                            error(marker, ANNOTATION, Construct.STATEMENT, "Expected field identifier");
                            return;
                        }
                        consumeTokenAs(null);
                        if (!isToken(SchemaLexer.EQUALS)) {
                            error(marker, ANNOTATION, Construct.STATEMENT, "Expected '='");
                            return;
                        }
                        consumeTokenAs(null);
                        parseAnnotationField();

                        if(isToken(SchemaLexer.RPARENTHESES)) {
                            consumeTokenAs(null);
                            break;
                        }

                        if(!isToken(SchemaLexer.COMMA)) {
                            error(marker, ANNOTATION, Construct.STATEMENT, "Expected ',' or end of annotation");
                            return;
                        }
                        consumeTokenAs(null);
                    }
                } else {
                    parseAnnotationFieldArray();
                }
            }

            if(!isToken(SchemaLexer.RBRACKET)) {
                error(marker, ANNOTATION, Construct.STATEMENT, "Expected end of annotation ']'");
                return;
            }
            consumeTokenAs(null);

            marker.done(ANNOTATION);
        }

        private void parseAnnotationFieldArray() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(null);
            while (true) {
                if(builder.getTokenText() == null) { //Something gone wrong. Invalid input?
                    break;
                }
                parseAnnotationField();
                if(isToken(SchemaLexer.RPARENTHESES)) {
                    break;
                }
                if(!isToken(SchemaLexer.COMMA)) {
                    error(marker, ANNOTATION_FIELD_ARRAY, Construct.STATEMENT, "Expected ',' or end of array");
                    return;
                }
                consumeTokenAs(null);
            }
            consumeTokenAs(null);
            marker.done(ANNOTATION_FIELD_ARRAY);
        }

        private void parseAnnotationField() {
            PsiBuilder.Marker marker = builder.mark();
            for(;;) {
                if(builder.getTokenText() != null && OPTION_PATTERN.matcher(builder.getTokenText()).matches()) { //If the text matches the option pattern, or its a number, or its a '.' and the previous match was a number
                    boolean num = isToken(SchemaLexer.INTEGER);
                    consumeTokenAs(OPTION_VALUE);
                    if(num && isIdentifier(".")) { //If the next thing is a decimal point
                        consumeTokenAs(OPTION_VALUE);
                        if(!isToken(SchemaLexer.INTEGER)) {
                            error(marker, ANNOTATION_FIELD, Construct.STATEMENT, "Cannot have a decimal with no decimal point");
                            return;
                        }
                        consumeTokenAs(OPTION_VALUE);
                    }
                    break;
                }
                if(isToken(SchemaLexer.LBRACKET)) { //Array
                    consumeTokenAs(null);
                    if(isToken(SchemaLexer.RBRACKET)) { //Empty array
                        consumeTokenAs(null);
                    } else {
                        while(true) {
                            parseAnnotationField();

                            if(isToken(SchemaLexer.RBRACKET)) {
                                consumeTokenAs(null);
                                break;
                            }
                            if(!isToken(SchemaLexer.COMMA)) {
                                error(marker, ANNOTATION_FIELD, Construct.STATEMENT, "Expected ',' or end of array");
                                return;
                            }
                            consumeTokenAs(null);
                        }
                    }
                    break;
                }

                if(isToken(SchemaLexer.LBRACE)) { //Map
                    consumeTokenAs(null);
                    if(isToken(SchemaLexer.RBRACE)) { //Empty map
                        consumeTokenAs(null);
                    } else {
                        while(true) {
                            parseAnnotationField();
                            if(!isToken(SchemaLexer.COLON)) {
                                error(marker, ANNOTATION_FIELD, Construct.STATEMENT, "Expected ':' in map");
                                return;
                            }
                            consumeTokenAs(TYPE_NAME); // ':'
                            parseAnnotationField();

                            if(isToken(SchemaLexer.RBRACE)) {
                                consumeTokenAs(null);
                                break;
                            }
                            if(!isToken(SchemaLexer.COMMA)) {
                                error(marker, ANNOTATION_FIELD, Construct.STATEMENT, "Expected ',' or end of map");
                                return;
                            }
                            consumeTokenAs(null);
                        }
                    }
                    break;
                }

                if(isToken(SchemaLexer.IDENTIFIER)) {
                    if(builder.lookAhead(1) == SchemaLexer.LPARENTHESES) { //Initiate a new object
                        RangedNode node = new RangedNode("Method Initializing");

                        //Make all '.' in the string white
                        int off = 0;
                        for (int i = 0; i < builder.getTokenText().toCharArray().length; i++) {
                            if(builder.getTokenText().toCharArray()[i] == '.') {
                                node.addEntry(off, i, DefaultLanguageHighlighterColors.METADATA);
                                off = i + 1;
                            }
                        }
                        node.addEntry(off, builder.getTokenText().length(), DefaultLanguageHighlighterColors.METADATA);
                        consumeTokenAs(node);

                        parseAnnotationFieldArray();
                        break;
                    } else { //Enum value
                        int index = builder.getTokenText().indexOf('.');
                        if(index == -1) {
                            consumeTokenAs(TYPE_NAME); //Shouldn't happen?
                            break;
                        }
                        consumeTokenAs(new RangedNode("Enum Reference")
                                .addEntry(0, index, DefaultLanguageHighlighterColors.METADATA)
                                .addEntry(index + 1, builder.getTokenText().length(), DefaultLanguageHighlighterColors.NUMBER));
                        break;
                    }
                }
                break;
            }
            marker.done(ANNOTATION_FIELD);
        }


        private void parseCommandDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, COMMAND_DEFINITION, Construct.STATEMENT, "Expected command response after 'command'.");
                return;
            }
            String response = getIdentifier();
            consumeTokenAs(TYPE_NAME);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, COMMAND_DEFINITION, Construct.STATEMENT,
                        "Expected command name after 'command %s'.", response);
                return;
            }
            String name = getIdentifier();
            consumeTokenAs(FIELD_NAME);
            if (!isToken(SchemaLexer.LPARENTHESES)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT,
                        "Expected '(' after 'command %s %s'.", response, name);
                return;
            }
            consumeTokenAs(null);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT,
                        "Expected command request after 'command %s %s('.", response, name);
                return;
            }
            String request = getIdentifier();
            consumeTokenAs(TYPE_NAME);
            if (!isToken(SchemaLexer.RPARENTHESES)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT,
                        "Expected ')' after 'command %s %s(%s'.", response, name, request);
                return;
            }
            consumeTokenAs(null);
            if (!isToken(SchemaLexer.SEMICOLON)) {
                error(marker, FIELD_DEFINITION, Construct.STATEMENT,
                        "Expected ';' after 'command %s %s(%s)'.", response, name, request);
                return;
            }
            consumeTokenAs(null);
            marker.done(COMMAND_DEFINITION);
        }

        private void parseEnumDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, ENUM_DEFINITION, Construct.BRACES, "Expected identifier after '%s'.", KEYWORD_ENUM);
                return;
            }
            String name = getIdentifier();
            consumeTokenAs(DEFINITION_NAME);
            if (!isToken(SchemaLexer.LBRACE)) {
                error(marker, ENUM_DEFINITION, Construct.BRACES, "Expected '{' after '%s %s'.", KEYWORD_ENUM, name);
                return;
            }
            consumeTokenAs(null);
            parseEnumContents();
            if (!isToken(SchemaLexer.RBRACE)) {
                error(marker, ENUM_DEFINITION, Construct.BRACES,
                      "Invalid '%s' inside %s %s.", getTokenText(), KEYWORD_ENUM, name);
                return;
            }
            consumeTokenAs(null);
            marker.done(ENUM_DEFINITION);
        }

        private void parseTypeDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, TYPE_DEFINITION, Construct.BRACES, "Expected identifier after '%s'.", KEYWORD_TYPE);
                return;
            }
            String name = getIdentifier();
            consumeTokenAs(DEFINITION_NAME);
            if (!isToken(SchemaLexer.LBRACE)) {
                error(marker, TYPE_DEFINITION, Construct.BRACES, "Expected '{' after '%s %s'.", KEYWORD_TYPE, name);
                return;
            }
            consumeTokenAs(null);
            parseTypeContents();
            if (!isToken(SchemaLexer.RBRACE)) {
                error(marker, TYPE_DEFINITION, Construct.BRACES,
                      "Invalid '%s' inside %s %s.", getTokenText(), KEYWORD_TYPE, name);
                return;
            }
            consumeTokenAs(null);
            marker.done(TYPE_DEFINITION);
        }

        private void parseComponentDefinition() {
            PsiBuilder.Marker marker = builder.mark();
            consumeTokenAs(KEYWORD);
            if (!isToken(SchemaLexer.IDENTIFIER)) {
                error(marker, COMPONENT_DEFINITION, Construct.BRACES,
                      "Expected identifier after '%s'.", KEYWORD_COMPONENT);
                return;
            }
            String name = getIdentifier();
            consumeTokenAs(DEFINITION_NAME);
            if (!isToken(SchemaLexer.LBRACE)) {
                error(marker, COMPONENT_DEFINITION, Construct.BRACES,
                      "Expected '{' after '%s %s'.", KEYWORD_COMPONENT, name);
                return;
            }
            consumeTokenAs(null);
            parseComponentContents();
            if (!isToken(SchemaLexer.RBRACE)) {
                error(marker, COMPONENT_DEFINITION, Construct.BRACES,
                      "Invalid '%s' inside %s %s.", getTokenText(), KEYWORD_COMPONENT, name);
                return;
            }
            consumeTokenAs(null);
            marker.done(COMPONENT_DEFINITION);
        }

        private void parseTopLevelDefinition() {
            if (isIdentifier(KEYWORD_PACKAGE)) {
                parsePackageDefinition();
            } else if (isIdentifier(KEYWORD_IMPORT)) {
                parseImportDefinition();
            } else if (isIdentifier(KEYWORD_ENUM)) {
                parseEnumDefinition();
            } else if (isIdentifier(KEYWORD_TYPE)) {
                parseTypeDefinition();
            } else if (isIdentifier(KEYWORD_COMPONENT)) {
                parseComponentDefinition();
            } else if(builder.getTokenText() != null && builder.getTokenText().equals(KEYWORD_ANNOTATION_START)) {
                parseAnnotation();
            } else {
                error(null, null, Construct.TOP_LEVEL,
                      "Expected '%s', '%s', '%s', '%s' or '%s' definition at top-level.",
                      KEYWORD_PACKAGE, KEYWORD_IMPORT, KEYWORD_ENUM, KEYWORD_TYPE, KEYWORD_COMPONENT);
            }
        }

        public void parseSchemaFile(@NotNull IElementType root) {
            PsiBuilder.Marker marker = builder.mark();
            while (builder.getTokenType() != null && !builder.eof()) {
                parseTopLevelDefinition();
            }
            marker.done(root);
        }
    }
}
