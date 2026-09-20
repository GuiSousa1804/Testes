package br.edu.ifpr.pedidos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrato de ItemPedido(sku, precoCentavos, quantidade, estoque, pesoGramas, fragil):
 * SKU não nulo/nem branco; preço 1..1.000.000; quantidade 0..100; estoque >= 0;
 * peso unitário 1..100.000. Violações lançam IllegalArgumentException.
 */
class ItemPedidoTest {

    // Fábricas auxiliares: variam apenas o campo que cada teste quer isolar.
    private static ItemPedido comSku(String sku)   { return new ItemPedido(sku, 100, 1, 1, 100, false); }
    private static ItemPedido comPreco(long preco) { return new ItemPedido("A", preco, 1, 1, 100, false); }
    private static ItemPedido comQuantidade(int q) { return new ItemPedido("A", 100, q, 100, 100, false); }
    private static ItemPedido comEstoque(int e)    { return new ItemPedido("A", 100, 1, e, 100, false); }
    private static ItemPedido comPeso(int p)       { return new ItemPedido("A", 100, 1, 1, p, false); }

    // ---------- construção válida ----------

    @Test
    void deveGuardarTodosOsCamposInformados() {
        ItemPedido item = new ItemPedido("LIVRO-JAVA", 10_000, 3, 5, 800, true);

        assertAll(
            () -> assertEquals("LIVRO-JAVA", item.sku()),
            () -> assertEquals(10_000L, item.precoCentavos()),
            () -> assertEquals(3, item.quantidade()),
            () -> assertEquals(5, item.estoque()),
            () -> assertEquals(800, item.pesoGramas()),
            () -> assertTrue(item.fragil())
        );
    }

    // ---------- SKU ----------

    @ParameterizedTest(name = "sku [{0}] deve ser rejeitado")
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void deveRejeitarSkuVazioOuBranco(String sku) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> comSku(sku));

        assertEquals("SKU obrigatório", erro.getMessage());
    }

    @Test
    void deveRejeitarSkuNulo() {
        assertThrows(IllegalArgumentException.class, () -> comSku(null));
    }

    @Test
    void deveAceitarSkuComUmUnicoCaractere() {
        assertEquals("X", comSku("X").sku());
    }

    // ---------- preço: limites 1 e 1.000.000 ----------

    @ParameterizedTest(name = "preco {0} deve ser rejeitado")
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 1_000_001, Long.MAX_VALUE})
    void deveRejeitarPrecoForaDoIntervalo(long preco) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> comPreco(preco));

        assertEquals("Preço inválido", erro.getMessage());
    }

    @ParameterizedTest(name = "preco {0} deve ser aceito")
    @ValueSource(longs = {1, 2, 500_000, 999_999, 1_000_000})
    void deveAceitarPrecoDentroDoIntervalo(long preco) {
        assertEquals(preco, comPreco(preco).precoCentavos());
    }

    // ---------- quantidade: limites 0 e 100 ----------

    @ParameterizedTest(name = "quantidade {0} deve ser rejeitada")
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 101, Integer.MAX_VALUE})
    void deveRejeitarQuantidadeForaDoIntervalo(int quantidade) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> comQuantidade(quantidade));

        assertEquals("Quantidade inválida", erro.getMessage());
    }

    @ParameterizedTest(name = "quantidade {0} deve ser aceita")
    @ValueSource(ints = {0, 1, 50, 99, 100})
    void deveAceitarQuantidadeDentroDoIntervalo(int quantidade) {
        assertEquals(quantidade, comQuantidade(quantidade).quantidade());
    }

    // ---------- estoque: limite 0 ----------

    @ParameterizedTest(name = "estoque {0} deve ser rejeitado")
    @ValueSource(ints = {Integer.MIN_VALUE, -1})
    void deveRejeitarEstoqueNegativo(int estoque) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> comEstoque(estoque));

        assertEquals("Estoque inválido", erro.getMessage());
    }

    @ParameterizedTest(name = "estoque {0} deve ser aceito")
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void deveAceitarEstoqueNaoNegativo(int estoque) {
        assertEquals(estoque, comEstoque(estoque).estoque());
    }

    // ---------- peso: limites 1 e 100.000 ----------

    @ParameterizedTest(name = "peso {0} deve ser rejeitado")
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 100_001, Integer.MAX_VALUE})
    void deveRejeitarPesoForaDoIntervalo(int peso) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> comPeso(peso));

        assertEquals("Peso inválido", erro.getMessage());
    }

    @ParameterizedTest(name = "peso {0} deve ser aceito")
    @ValueSource(ints = {1, 2, 50_000, 99_999, 100_000})
    void deveAceitarPesoDentroDoIntervalo(int peso) {
        assertEquals(peso, comPeso(peso).pesoGramas());
    }

    // ---------- totalCentavos ----------

    @ParameterizedTest(name = "preco {0} x quantidade {1} = {2}")
    @CsvSource({
        "10000, 1, 10000",
        "2550,  3, 7650",
        "1,     1, 1",
        "999,   0, 0",          // linha inativa não tem valor
        "1000000, 100, 100000000" // maior valor possível; exige long, não int
    })
    void deveCalcularTotalComoPrecoVezesQuantidade(long preco, int quantidade, long esperado) {
        ItemPedido item = new ItemPedido("A", preco, quantidade, 100, 100, false);

        assertEquals(esperado, item.totalCentavos());
    }

    // ---------- disponivel: quantidade <= estoque ----------

    @ParameterizedTest(name = "quantidade {0} e estoque {1}: disponivel = {2}")
    @CsvSource({
        "1, 5, true",    // abaixo do estoque
        "5, 5, true",    // exatamente igual ao estoque (fronteira)
        "6, 5, false",   // uma unidade acima do estoque
        "1, 0, false",   // sem nenhum estoque
        "0, 0, true",    // linha inativa nunca depende de estoque
        "100, 100, true",
        "100, 99, false"
    })
    void deveInformarDisponibilidadeComparandoQuantidadeComEstoque(int quantidade, int estoque, boolean esperado) {
        ItemPedido item = new ItemPedido("A", 100, quantidade, estoque, 100, false);

        assertEquals(esperado, item.disponivel());
    }

    // ---------- ordem/independência das validações ----------

    @Test
    void deveLancarExcecaoQuandoVariosCamposSaoInvalidos() {
        assertThrows(IllegalArgumentException.class,
            () -> new ItemPedido(" ", -1, 500, -5, 0, false));
    }
}
