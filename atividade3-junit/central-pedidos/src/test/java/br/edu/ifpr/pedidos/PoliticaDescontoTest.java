package br.edu.ifpr.pedidos;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras de PoliticaDesconto (valores em centavos, divisões truncadas):
 * 1. VIP 10%; comum 5% se subtotal >= 50.000; demais 0.
 * 2. Cupom nulo/branco mantém o desconto; cupom sofre trim + maiúsculas.
 * 3. BEMVINDO: +2.000 se 0 compras anteriores e subtotal >= 10.000.
 * 4. EXTRA10: +10% se subtotal >= 20.000.
 * 5. Cupom conhecido sem elegibilidade não soma; desconhecido lança IllegalArgumentException.
 * 6. Desconto combinado limitado a 20% do subtotal.
 *
 * Todos os valores esperados foram calculados à mão a partir dessas regras.
 */
class PoliticaDescontoTest {

    private final PoliticaDesconto politica = new PoliticaDesconto();

    private static final Cliente COMUM_NOVO   = new Cliente(false, false, 0);
    private static final Cliente COMUM_ANTIGO = new Cliente(false, false, 4);
    private static final Cliente VIP_NOVO     = new Cliente(true, false, 0);
    private static final Cliente VIP_ANTIGO   = new Cliente(true, false, 4);

    // =====================================================================
    // Validação de entrada
    // =====================================================================

    @Test
    void deveRejeitarSubtotalNegativo() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> politica.calcular(COMUM_ANTIGO, -1, null));

        assertEquals("Subtotal negativo", erro.getMessage());
    }

    @Test
    void deveRejeitarSubtotalNegativoAntesDeAvaliarOCupom() {
        // Cupom desconhecido também seria erro, mas a mensagem prova qual validação disparou primeiro.
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> politica.calcular(COMUM_ANTIGO, -100, "INEXISTENTE"));

        assertEquals("Subtotal negativo", erro.getMessage());
    }

    @Test
    void deveAceitarSubtotalZeroSemDesconto() {
        assertEquals(0L, politica.calcular(COMUM_ANTIGO, 0, null));
        assertEquals(0L, politica.calcular(VIP_ANTIGO, 0, null));
    }

    // =====================================================================
    // Desconto base (sem cupom)
    // =====================================================================

    @ParameterizedTest(name = "VIP, subtotal {0}, sem cupom -> desconto {1}")
    @CsvSource({
        "1,      0",       // 10% de 1 trunca para 0
        "9,      0",       // 0,9 trunca para 0
        "10,     1",
        "999,    99",      // 99,9 trunca para 99
        "10000,  1000",
        "49999,  4999",    // VIP não depende do limiar de R$ 500,00
        "50000,  5000",
        "123457, 12345"    // 12345,7 trunca para 12345
    })
    void deveDar10PorCentoParaClienteVip(long subtotal, long esperado) {
        assertEquals(esperado, politica.calcular(VIP_ANTIGO, subtotal, null));
    }

    @ParameterizedTest(name = "comum, subtotal {0}, sem cupom -> desconto {1}")
    @CsvSource({
        "0,      0",
        "1,      0",
        "49999,  0",       // um centavo abaixo do limiar
        "50000,  2500",    // exatamente no limiar: 5%
        "50001,  2500",    // 2500,05 trunca para 2500
        "50021,  2501",    // 2501,05 trunca para 2501
        "100000, 5000",
        "10000000, 500000"
    })
    void deveDar5PorCentoParaClienteComumApenasAPartirDeQuinhentosReais(long subtotal, long esperado) {
        assertEquals(esperado, politica.calcular(COMUM_ANTIGO, subtotal, null));
    }

    @Test
    void naoDeveDependerDoHistoricoDeComprasParaODescontoBase() {
        assertEquals(politica.calcular(COMUM_NOVO, 60_000, null),
                     politica.calcular(COMUM_ANTIGO, 60_000, null));
    }

    @Test
    void naoDeveConsiderarClienteBloqueadoNaPolitica() {
        // O bloqueio é tratado pelo serviço; a política apenas calcula o desconto.
        Cliente bloqueadoVip = new Cliente(true, true, 1);

        assertEquals(1_000L, politica.calcular(bloqueadoVip, 10_000, null));
    }

    // =====================================================================
    // Cupom nulo ou branco mantém o desconto base
    // =====================================================================

    @ParameterizedTest(name = "cupom [{0}] mantém o desconto base")
    @NullSource
    @ValueSource(strings = {"", " ", "     ", "\t", "\n", " \t \n "})
    void deveManterDescontoBaseParaCupomNuloOuBranco(String cupom) {
        assertAll(
            () -> assertEquals(2_500L, politica.calcular(COMUM_ANTIGO, 50_000, cupom)),
            () -> assertEquals(1_000L, politica.calcular(VIP_ANTIGO, 10_000, cupom)),
            () -> assertEquals(0L, politica.calcular(COMUM_NOVO, 10_000, cupom))
        );
    }

    // =====================================================================
    // Cupom BEMVINDO
    // =====================================================================

    @Test
    void deveSomarVinteReaisComBemvindoParaPrimeiraCompraNoLimiar() {
        // Subtotal 10.000, 0 compras: base 0 + 2.000 = 2.000; teto 20% = 2.000 (igual, não corta).
        assertEquals(2_000L, politica.calcular(COMUM_NOVO, 10_000, "BEMVINDO"));
    }

    @Test
    void deveSomarVinteReaisComBemvindoAcimaDoLimiar() {
        // Subtotal 15.000: base 0 + 2.000; teto 3.000.
        assertEquals(2_000L, politica.calcular(COMUM_NOVO, 15_000, "BEMVINDO"));
    }

    @Test
    void naoDeveSomarBemvindoUmCentavoAbaixoDoLimiar() {
        assertEquals(0L, politica.calcular(COMUM_NOVO, 9_999, "BEMVINDO"));
    }

    @Test
    void naoDeveSomarBemvindoQuandoJaHouveCompras() {
        assertEquals(0L, politica.calcular(COMUM_ANTIGO, 10_000, "BEMVINDO"));
    }

    @Test
    void naoDeveSomarBemvindoComUmaUnicaCompraAnterior() {
        assertEquals(0L, politica.calcular(new Cliente(false, false, 1), 10_000, "BEMVINDO"));
    }

    @Test
    void naoDeveSomarBemvindoQuandoAmbasAsCondicoesFalham() {
        assertEquals(0L, politica.calcular(COMUM_ANTIGO, 9_999, "BEMVINDO"));
    }

    @Test
    void deveSomarBemvindoAoDescontoComumDeCincoPorCento() {
        // 50.000: base 2.500 + 2.000 = 4.500; teto 10.000.
        assertEquals(4_500L, politica.calcular(COMUM_NOVO, 50_000, "BEMVINDO"));
    }

    @Test
    void deveManterDescontoBaseQuandoBemvindoNaoEElegivel() {
        // 60.000, cliente antigo: 5% = 3.000; BEMVINDO conhecido mas inelegível não altera.
        assertEquals(3_000L, politica.calcular(COMUM_ANTIGO, 60_000, "BEMVINDO"));
    }

    // =====================================================================
    // Cupom EXTRA10
    // =====================================================================

    @Test
    void deveSomarDezPorCentoComExtra10NoLimiar() {
        // 20.000 comum: base 0 + 2.000; teto 4.000.
        assertEquals(2_000L, politica.calcular(COMUM_ANTIGO, 20_000, "EXTRA10"));
    }

    @Test
    void naoDeveSomarExtra10UmCentavoAbaixoDoLimiar() {
        assertEquals(0L, politica.calcular(COMUM_ANTIGO, 19_999, "EXTRA10"));
    }

    @Test
    void deveTruncarOsDezPorCentoDoExtra10() {
        // 20.019 x 10 / 100 = 2.001,9 -> 2.001
        assertEquals(2_001L, politica.calcular(COMUM_ANTIGO, 20_019, "EXTRA10"));
    }

    @Test
    void deveSomarExtra10AoDescontoBaseDeCincoPorCento() {
        // 50.000: 2.500 + 5.000 = 7.500; teto 10.000.
        assertEquals(7_500L, politica.calcular(COMUM_ANTIGO, 50_000, "EXTRA10"));
    }

    @Test
    void deveSomarExtra10ao10PorCentoDoVipEChegarExatamenteNoTeto() {
        // VIP 20.000: 2.000 + 2.000 = 4.000 = teto (20% de 20.000): não é cortado.
        assertEquals(4_000L, politica.calcular(VIP_ANTIGO, 20_000, "EXTRA10"));
    }

    @Test
    void deveTruncarTetoEDescontoQuandoVipUsaExtra10() {
        // VIP 20.019: 2.001 (10%) + 2.001 (10%) = 4.002; teto 20.019 x 20 / 100 = 4.003,8 -> 4.003.
        assertEquals(4_002L, politica.calcular(VIP_ANTIGO, 20_019, "EXTRA10"));
    }

    @Test
    void naoDeveSomarExtra10ParaVipAbaixoDoLimiar() {
        assertEquals(1_999L, politica.calcular(VIP_ANTIGO, 19_999, "EXTRA10"));
    }

    // =====================================================================
    // Teto de 20 %
    // =====================================================================

    @Test
    void deveLimitarAoTetoQuandoVipUsaBemvindoNoLimiar() {
        // VIP novo 10.000: 1.000 + 2.000 = 3.000; teto = 2.000 -> corta para 2.000.
        assertEquals(2_000L, politica.calcular(VIP_NOVO, 10_000, "BEMVINDO"));
    }

    @Test
    void deveLimitarAoTetoComValorTruncado() {
        // VIP novo 10.500: 1.050 + 2.000 = 3.050; teto = 2.100 -> 2.100.
        assertEquals(2_100L, politica.calcular(VIP_NOVO, 10_500, "BEMVINDO"));
    }

    @Test
    void naoDeveLimitarQuandoDescontoFicaAbaixoDoTeto() {
        // VIP novo 30.000: 3.000 + 2.000 = 5.000; teto 6.000 -> 5.000 (sem corte).
        assertEquals(5_000L, politica.calcular(VIP_NOVO, 30_000, "BEMVINDO"));
    }

    @Test
    void deveRespeitarOTetoNaFronteiraEntreCorteESemCorte() {
        // VIP novo 20.000: 2.000 + 2.000 = 4.000 = teto 4.000 (igual: não corta e o valor é o mesmo).
        assertEquals(4_000L, politica.calcular(VIP_NOVO, 20_000, "BEMVINDO"));
    }

    // =====================================================================
    // Normalização do cupom (trim + maiúsculas) e cupons inválidos
    // =====================================================================

    @ParameterizedTest(name = "cupom [{0}] equivale a BEMVINDO")
    @ValueSource(strings = {"BEMVINDO", "bemvindo", "BemVindo", "  BEMVINDO  ", "\tbemvindo\n", " bemVINDO"})
    void deveNormalizarCupomBemvindo(String cupom) {
        assertEquals(2_000L, politica.calcular(COMUM_NOVO, 10_000, cupom));
    }

    @ParameterizedTest(name = "cupom [{0}] equivale a EXTRA10")
    @ValueSource(strings = {"EXTRA10", "extra10", "Extra10", "  extra10 ", "\nEXTRA10\t"})
    void deveNormalizarCupomExtra10(String cupom) {
        assertEquals(2_000L, politica.calcular(COMUM_ANTIGO, 20_000, cupom));
    }

    @ParameterizedTest(name = "cupom [{0}] é desconhecido")
    @ValueSource(strings = {"PROMO", "BEM VINDO", "BEMVINDO2", "EXTRA", "EXTRA100", "EXTRA-10", "0", "x", "DESCONTO50"})
    void deveRejeitarCupomDesconhecido(String cupom) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> politica.calcular(COMUM_ANTIGO, 60_000, cupom));

        assertEquals("Cupom desconhecido", erro.getMessage());
    }

    @Test
    void deveRejeitarCupomDesconhecidoMesmoParaClienteVipEPedidoPequeno() {
        assertThrows(IllegalArgumentException.class, () -> politica.calcular(VIP_NOVO, 1, "PROMO"));
    }

    @Test
    void deveRejeitarCupomDesconhecidoMesmoComSubtotalZero() {
        assertThrows(IllegalArgumentException.class, () -> politica.calcular(COMUM_NOVO, 0, "PROMO"));
    }

    @Test
    void deveConsiderarCupomConhecidoSemElegibilidadeComoDescontoBaseSemErro() {
        assertDoesNotThrow(() -> politica.calcular(COMUM_ANTIGO, 1, "BEMVINDO"));
        assertDoesNotThrow(() -> politica.calcular(COMUM_ANTIGO, 1, "EXTRA10"));
    }

    @Test
    void deveNormalizarCupomIndependenteDoIdiomaPadraoDaMaquina() {
        // Em turco, "i".toUpperCase() produziria "İ" (com ponto) e "bemvindo" deixaria de casar.
        // A implementação usa Locale.ROOT; este teste garante que o resultado não depende do locale.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals(2_000L, politica.calcular(COMUM_NOVO, 10_000, "bemvindo"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
