package com.suko.zeroparse;

import com.suko.pool.AutoGrowConfig;

/**
 * Configuration for ZeroParse runtime behavior.
 * 
 * <p>This class provides configuration options for pooling, parsing limits,
 * and performance tuning.</p>
 */
public final class ZeroParseConfig {
    
    // Pool configuration
    private final int parserPoolSize;
    private final int objectPoolSize;
    private final int arrayPoolSize;
    private final int stringPoolSize;
    private final int numberPoolSize;
    private final int stripedPoolStripes;
    private final int stripedPoolStripeSize;
    private final AutoGrowConfig autoGrowConfig;
    
    // Parsing limits
    private final int maxNestingDepth;
    private final int maxStringLength;
    private final int maxNumberLength;
    private final int maxArrayLength;
    private final int maxObjectFields;
    
    // Performance options
    private final boolean enableLazyParsing;
    private final boolean enableValidation;
    private final NumberParsingMode numberParsingMode;
    private final ParserMode parserMode;
    
    private ZeroParseConfig(Builder builder) {
        this.parserPoolSize = builder.parserPoolSize;
        this.objectPoolSize = builder.objectPoolSize;
        this.arrayPoolSize = builder.arrayPoolSize;
        this.stringPoolSize = builder.stringPoolSize;
        this.numberPoolSize = builder.numberPoolSize;
        this.stripedPoolStripes = builder.stripedPoolStripes;
        this.stripedPoolStripeSize = builder.stripedPoolStripeSize;
        this.autoGrowConfig = builder.autoGrowConfig;
        
        this.maxNestingDepth = builder.maxNestingDepth;
        this.maxStringLength = builder.maxStringLength;
        this.maxNumberLength = builder.maxNumberLength;
        this.maxArrayLength = builder.maxArrayLength;
        this.maxObjectFields = builder.maxObjectFields;
        
        this.enableLazyParsing = builder.enableLazyParsing;
        this.enableValidation = builder.enableValidation;
        this.numberParsingMode = builder.numberParsingMode;
        this.parserMode = builder.parserMode;
    }
    
    // Pool configuration getters
    public int getParserPoolSize() { return parserPoolSize; }
    public int getObjectPoolSize() { return objectPoolSize; }
    public int getArrayPoolSize() { return arrayPoolSize; }
    public int getStringPoolSize() { return stringPoolSize; }
    public int getNumberPoolSize() { return numberPoolSize; }
    public int getStripedPoolStripes() { return stripedPoolStripes; }
    public int getStripedPoolStripeSize() { return stripedPoolStripeSize; }
    public AutoGrowConfig getAutoGrowConfig() { return autoGrowConfig; }
    
    // Parsing limits getters
    public int getMaxNestingDepth() { return maxNestingDepth; }
    public int getMaxStringLength() { return maxStringLength; }
    public int getMaxNumberLength() { return maxNumberLength; }
    public int getMaxArrayLength() { return maxArrayLength; }
    public int getMaxObjectFields() { return maxObjectFields; }
    
    // Performance options getters
    public boolean isLazyParsingEnabled() { return enableLazyParsing; }
    public boolean isValidationEnabled() { return enableValidation; }
    public NumberParsingMode getNumberParsingMode() { return numberParsingMode; }
    public ParserMode getParserMode() { return parserMode; }
    
    /**
     * Number parsing mode options.
     */
    public enum NumberParsingMode {
        /** Fast double parsing (default) */
        FAST_DOUBLE,
        /** BigDecimal parsing for precision */
        BIG_DECIMAL,
        /** Lazy parsing - parse on demand */
        LAZY
    }
    
    /**
     * Parser mode options.
     */
    public enum ParserMode {
        /** Stack-based lazy parser */
        STACK
    }
    
    /**
     * Builder for ZeroParseConfig.
     */
    public static final class Builder {
        // Pool configuration defaults
        private int parserPoolSize = 64;
        private int objectPoolSize = 256;
        private int arrayPoolSize = 256;
        private int stringPoolSize = 512;
        private int numberPoolSize = 512;
        private int stripedPoolStripes = 4;
        private int stripedPoolStripeSize = 64;
        private AutoGrowConfig autoGrowConfig = AutoGrowConfig.withDefaults(64);
        
        // Parsing limits defaults
        private int maxNestingDepth = 100;
        private int maxStringLength = 1024 * 1024; // 1MB
        private int maxNumberLength = 1000;
        private int maxArrayLength = 100_000;
        private int maxObjectFields = 100_000;
        
        // Performance options defaults
        private boolean enableLazyParsing = true;
        private boolean enableValidation = true;
        private NumberParsingMode numberParsingMode = NumberParsingMode.FAST_DOUBLE;
        private ParserMode parserMode = ParserMode.STACK;
        
        /**
         * Set pool sizes.
         */
        public Builder poolSizes(int parser, int object, int array, int string, int number) {
            this.parserPoolSize = parser;
            this.objectPoolSize = object;
            this.arrayPoolSize = array;
            this.stringPoolSize = string;
            this.numberPoolSize = number;
            return this;
        }
        
        /**
         * Set striped pool configuration.
         */
        public Builder stripedPool(int stripes, int stripeSize) {
            this.stripedPoolStripes = stripes;
            this.stripedPoolStripeSize = stripeSize;
            return this;
        }
        
        /**
         * Set auto-grow configuration.
         */
        public Builder autoGrow(AutoGrowConfig config) {
            this.autoGrowConfig = config;
            return this;
        }
        
        /**
         * Set parsing limits.
         */
        public Builder limits(int maxNesting, int maxString, int maxNumber, int maxArray, int maxFields) {
            this.maxNestingDepth = maxNesting;
            this.maxStringLength = maxString;
            this.maxNumberLength = maxNumber;
            this.maxArrayLength = maxArray;
            this.maxObjectFields = maxFields;
            return this;
        }
        
        /**
         * Enable or disable lazy parsing.
         */
        public Builder lazyParsing(boolean enabled) {
            this.enableLazyParsing = enabled;
            return this;
        }
        
        /**
         * Enable or disable validation.
         */
        public Builder validation(boolean enabled) {
            this.enableValidation = enabled;
            return this;
        }
        
        /**
         * Set number parsing mode.
         */
        public Builder numberParsingMode(NumberParsingMode mode) {
            this.numberParsingMode = mode;
            return this;
        }
        
        /**
         * Set parser mode.
         */
        public Builder parserMode(ParserMode mode) {
            this.parserMode = mode;
            return this;
        }
        
        /**
         * Build the configuration.
         */
        public ZeroParseConfig build() {
            return new ZeroParseConfig(this);
        }
    }
    
    /**
     * Create a new builder with default values.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a default configuration.
     */
    public static ZeroParseConfig defaults() {
        return builder().build();
    }
}
