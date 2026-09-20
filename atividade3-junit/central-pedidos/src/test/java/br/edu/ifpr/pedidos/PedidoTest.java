package br.edu.ifpr.pedidos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pedido(itens, uf, expresso, cupom) e seus quatro métodos de consulta com laço:
 * subtotalCentavos, pesoGramas, temFragil e estoqueSuficiente.
 * Cada laço é exercitado com zero, uma e várias iterações.
 */
class PedidoTest {

    // item(preco, quantidade, estoque, pesoUnitario, fragil)
    private static ItemPedido item(long preco, int quantidade, int estoque, int peso, boolean fragil) {
        return new ItemPedido("SKU", preco, quantidade, estoque, peso, fragil);
    }

    private static Pedido pedido(ItemPedido... itens) {
        return new Pedido(List.of(itens), "PR", false, null);
    }

    // =====================================================================
    // Construção
    // =====================================================================

    @Test
    void deveGuardarUfExpressoECupom() {
        Pedido pedido = new Pedido(List.of(item(100, 1, 1, 100, false)), "SP", true, "BEMVINDO");

        assertAll(
            () -> assertEquals("SP", pedido.uf()),
            () -> assertTrue(pedido.expresso()),
            () -> assertEquals("BEMVINDO", pedido.cupom()),
            () -> assertEquals(1, pedido.itens().size())
        );
    }

    @Test
    void deveAceitarCupomNuloEBranco() {
        assertNull(new Pedido(List.of(), "PR", false, null).cupom());
        assertEquals("  ", new Pedido(List.of(), "PR", false, "  ").cupom());
    }

    @Test
    void deveRejeitarListaNula() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> new Pedido(null, "PR", false, null));

        assertEquals("Lista inválida", erro.getMessage());
    }

    @Test
    void deveAceitarListaVaziaNaConstrucao() {
        Pedido pedido = new Pedido(List.of(), "PR", false, null);

        assertTrue(pedido.itens().isEmpty());
    }

    @Test
    void deveAceitarExatamenteCemLinhas() {
        List<ItemPedido> cem = Collections.nCopies(100, item(100, 1, 1, 100, false));

        assertEquals(100, new Pedido(cem, "PR", false, null).itens().size());
    }

    @Test
    void deveRejeitarCentoEUmaLinhas() {
        List<ItemPedido> centoEUma = Collections.nCopies(101, item(100, 1, 1, 100, false));

        assertThrows(IllegalArgumentException.class, () -> new Pedido(centoEUma, "PR", false, null));
    }

    @Test
    void deveLancarNullPointerExceptionParaElementoNuloNaLista() {
        List<ItemPedido> comNulo = new ArrayList<>();
        comNulo.add(item(100, 1, 1, 100, false));
        comNulo.add(null);

        assertThrows(NullPointerException.class, () -> new Pedido(comNulo, "PR", false, null));
    }

    @ParameterizedTest(name = "uf [{0}] deve ser rejeitada")
    @NullSource
    @ValueSource(strings = {"", "P", "PRR", "pr", "Pr", "pR", "P1", "1P", "12", "P ", " P", "PR ", "P-", "ÁB", "P\n"})
    void deveRejeitarUfForaDoFormatoDuasLetrasMaiusculas(String uf) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> new Pedido(List.of(), uf, false, null));

        assertEquals("UF inválida", erro.getMessage());
    }

    @ParameterizedTest(name = "uf {0} deve ser aceita")
    @ValueSource(strings = {"PR", "SP", "RJ", "AA", "ZZ", "XX"})
    void deveAceitarQualquerParDeLetrasMaiusculas(String uf) {
        assertEquals(uf, new Pedido(List.of(), uf, false, null).uf());
    }

    @Test
    void deveCopiarAListaDefensivamente() {
        List<ItemPedido> original = new ArrayList<>();
        original.add(item(1_000, 1, 1, 100, false));
        Pedido pedido = new Pedido(original, "PR", false, null);

        // Alterar a lista original depois da construção não pode afetar o pedido.
        original.add(item(9_999, 1, 1, 100, false));
        original.clear();

        assertAll(
            () -> assertEquals(1, pedido.itens().size()),
            () -> assertEquals(1_000L, pedido.subtotalCentavos())
        );
    }

    @Test
    void deveExporListaImutavel() {
        Pedido pedido = pedido(item(100, 1, 1, 100, false));

        assertThrows(UnsupportedOperationException.class,
            () -> pedido.itens().add(item(100, 1, 1, 100, false)));
    }

    // =====================================================================
    // subtotalCentavos  (for com 'continue' para linhas inativas)
    // =====================================================================

    @Test
    void deveTerSubtotalZeroParaListaVazia() {
        assertEquals(0L, pedido().subtotalCentavos());
    }

    @Test
    void deveCalcularSubtotalDeUmaLinha() {
        assertEquals(7_650L, pedido(item(2_550, 3, 10, 100, false)).subtotalCentavos());
    }

    @Test
    void deveSomarSubtotalDeVariasLinhas() {
        Pedido pedido = pedido(
            item(10_000, 1, 5, 100, false),   // 10.000
            item(2_550, 3, 5, 100, false),    //  7.650
            item(199, 10, 10, 100, true));    //  1.990

        assertEquals(19_640L, pedido.subtotalCentavos());
    }

    @Test
    void deveIgnorarLinhaInativaNoSubtotal() {
        Pedido pedido = pedido(item(1_000_000, 0, 0, 100, false));

        assertEquals(0L, pedido.subtotalCentavos());
    }

    @Test
    void deveIgnorarLinhaInativaNoInicioNoMeioENoFimDaLista() {
        Pedido pedido = pedido(
            item(50_000, 0, 0, 100, false),  // inativa no início
            item(1_000, 2, 2, 100, false),   // 2.000
            item(70_000, 0, 0, 100, false),  // inativa no meio
            item(500, 4, 4, 100, false),     // 2.000
            item(90_000, 0, 0, 100, false)); // inativa no fim

        assertEquals(4_000L, pedido.subtotalCentavos());
    }

    @Test
    void deveSomarLinhasComSkuRepetido() {
        Pedido pedido = pedido(item(1_000, 1, 1, 100, false), item(1_000, 2, 2, 100, false));

        assertEquals(3_000L, pedido.subtotalCentavos());
    }

    @Test
    void deveSuportarSubtotalMaximoSemEstourarLong() {
        // 100 linhas x (1.000.000 x 100) = 10 bilhões de centavos: exige long.
        List<ItemPedido> cem = Collections.nCopies(100, item(1_000_000, 100, 100, 1, false));

        assertEquals(10_000_000_000L, new Pedido(cem, "PR", false, null).subtotalCentavos());
    }

    // =====================================================================
    // pesoGramas  (for simples: peso unitário x quantidade)
    // =====================================================================

    @Test
    void deveTerPesoZeroParaListaVazia() {
        assertEquals(0, pedido().pesoGramas());
    }

    @Test
    void deveCalcularPesoDeUmaLinhaComoPesoVezesQuantidade() {
        assertEquals(1_500, pedido(item(100, 3, 3, 500, false)).pesoGramas());
    }

    @Test
    void deveSomarPesoDeVariasLinhas() {
        Pedido pedido = pedido(
            item(100, 3, 3, 500, false),     // 1.500
            item(100, 2, 2, 1_000, false),   // 2.000
            item(100, 1, 1, 250, false));    //   250

        assertEquals(3_750, pedido.pesoGramas());
    }

    @Test
    void deveDesconsiderarPesoDeLinhaInativa() {
        Pedido pedido = pedido(item(100, 1, 1, 400, false), item(100, 0, 0, 100_000, false));

        assertEquals(400, pedido.pesoGramas());
    }

    @Test
    void deveSuportarPesoMaximoPossivelDeUmaLinha() {
        // 100.000 g x 100 unidades = 10.000.000 g, ainda dentro de int.
        assertEquals(10_000_000, pedido(item(100, 100, 100, 100_000, false)).pesoGramas());
    }

    // =====================================================================
    // temFragil  (for com retorno antecipado)
    // =====================================================================

    @Test
    void naoDeveTerFragilEmListaVazia() {
        assertFalse(pedido().temFragil());
    }

    @Test
    void deveDetectarUnicaLinhaFragilAtiva() {
        assertTrue(pedido(item(100, 1, 1, 100, true)).temFragil());
    }

    @Test
    void naoDeveTerFragilQuandoNenhumaLinhaEFragil() {
        assertFalse(pedido(item(100, 1, 1, 100, false), item(100, 2, 2, 100, false)).temFragil());
    }

    @Test
    void naoDeveContarFragilDeLinhaInativa() {
        // Item frágil com quantidade zero não conta como frágil.
        assertFalse(pedido(item(100, 0, 0, 100, true)).temFragil());
    }

    @Test
    void deveEncontrarFragilNaUltimaLinhaDepoisDeLinhasNaoFrageis() {
        Pedido pedido = pedido(
            item(100, 1, 1, 100, false),
            item(100, 1, 1, 100, false),
            item(100, 1, 1, 100, true));

        assertTrue(pedido.temFragil());
    }

    @Test
    void deveEncontrarFragilAtivoMesmoQuandoHaFragilInativoAntes() {
        Pedido pedido = pedido(item(100, 0, 0, 100, true), item(100, 1, 1, 100, true));

        assertTrue(pedido.temFragil());
    }

    @Test
    void deveDetectarFragilNaPrimeiraLinhaComOutrasLinhasDepois() {
        Pedido pedido = pedido(item(100, 1, 1, 100, true), item(100, 1, 1, 100, false));

        assertTrue(pedido.temFragil());
    }

    // =====================================================================
    // estoqueSuficiente  (for com 'break')
    // =====================================================================

    @Test
    void deveTerEstoqueSuficienteParaListaVazia() {
        assertTrue(pedido().estoqueSuficiente());
    }

    @Test
    void deveTerEstoqueSuficienteQuandoQuantidadeIgualAoEstoque() {
        assertTrue(pedido(item(100, 5, 5, 100, false)).estoqueSuficiente());
    }

    @Test
    void naoDeveTerEstoqueSuficienteQuandoUmaLinhaExcedeOEstoque() {
        assertFalse(pedido(item(100, 6, 5, 100, false)).estoqueSuficiente());
    }

    @Test
    void deveTerEstoqueSuficienteQuandoTodasAsLinhasTemEstoque() {
        Pedido pedido = pedido(
            item(100, 1, 10, 100, false),
            item(100, 5, 5, 100, false),
            item(100, 2, 3, 100, false));

        assertTrue(pedido.estoqueSuficiente());
    }

    @Test
    void deveDetectarFaltaDeEstoqueNaPrimeiraLinha() {
        Pedido pedido = pedido(item(100, 9, 1, 100, false), item(100, 1, 10, 100, false));

        assertFalse(pedido.estoqueSuficiente());
    }

    @Test
    void deveDetectarFaltaDeEstoqueNaUltimaLinha() {
        Pedido pedido = pedido(
            item(100, 1, 10, 100, false),
            item(100, 1, 10, 100, false),
            item(100, 9, 1, 100, false));

        assertFalse(pedido.estoqueSuficiente());
    }

    @Test
    void deveDetectarFaltaDeEstoqueNoMeioEInterromperAAnalise() {
        Pedido pedido = pedido(
            item(100, 1, 10, 100, false),
            item(100, 9, 1, 100, false),
            item(100, 1, 10, 100, false));

        assertFalse(pedido.estoqueSuficiente());
    }

    @Test
    void deveAvaliarEstoquePorLinhaMesmoComSkuRepetido() {
        // Duas linhas do mesmo SKU, cada uma com 3 unidades e estoque 3:
        // o estoque não é somado nem compartilhado entre as linhas.
        Pedido pedido = pedido(item(100, 3, 3, 100, false), item(100, 3, 3, 100, false));

        assertTrue(pedido.estoqueSuficiente());
    }

    @Test
    void naoDeveExigirEstoqueParaLinhaInativa() {
        assertTrue(pedido(item(100, 0, 0, 100, false)).estoqueSuficiente());
    }
}
