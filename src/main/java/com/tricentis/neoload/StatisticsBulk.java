package com.tricentis.neoload;

import java.util.List;

/**
 * @author lcharlois
 * @since 09/12/2021.
 */
class StatisticsBulk {
    private final List<BenchElement> newElements;
    private final List<ElementResults> values;

    StatisticsBulk(final List<BenchElement> newElements, final List<ElementResults> values) {
        this.newElements = newElements;
        this.values = values;
    }

    List<BenchElement> getNewElements() {
        return newElements;
    }

    List<ElementResults> getValues() {
        return values;
    }
}
