package br.edu.ifpr.pedidos;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras de CalculadoraFrete.calcular, aplicadas nesta ordem (valores em centavos):
 * 1. Base: PR 1.200; SP/RJ 2.000; demais 3.000.
 * 2. Acima de 2 kg: +300 por kg adicional OU fração (laço while).
 * 3. Líquido >= 30.000 e entrega normal: zera base e adicional de peso.
 * 4. VIP paga metade desse valor.
 * 5. Expresso: +1.500; item frágil ativo: +500 (uma única vez). Incidem mesmo com base zerada.
 *
 * O peso do pedido é construído com um único item de peso unitário informado (quantidade 1),
 * e o valor líquido é passado diretamente ao método.
 */
class CalculadoraFreteTest {

    private final CalculadoraFrete calculadora = new CalculadoraFrete();

    private static final Cliente COMUM = new Cliente(false, false, 1);
    private static final Cliente VIP   = new Cliente(true, false, 1);

    private static final long ABAIXO_DO_LIMIAR_GRATIS = 29_999;
    private static final long NO_LIMIAR_GRATIS        = 30_000;

    // Um item de quantidade 1 com o peso e a fragilidade desejados.
    private static ItemPedido item(int pesoGramas, boolean fragil) {
        return new ItemPedido("SKU", 100, 1, 1, pesoGramas, fragil);
    }

    private static Pedido pedido(String uf, int pesoGramas, boolean expresso, boolean fragil) {
        return new Pedido(List.of(item(pesoGramas, fragil)), uf, expresso, null);
    }

    private long frete(String uf, int peso, boolean expresso, boolean fragil, Cliente cliente, long liquido) {
        return calculadora.calcular(pedido(uf, peso, expresso, fragil), cliente, liquido);
    }

    // =====================================================================
    // Validação
    // =====================================================================

    @ParameterizedTest(name = "liquido {0} deve ser rejeitado")
    @ValueSource(longs = {-1, -30_000, Long.MIN_VALUE})
    void deveRejeitarValorLiquidoNegativo(long liquido) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> frete("PR", 1_000, false, false, COMUM, liquido));

        assertEquals("Valor líquido negativo", erro.getMessage());
    }

    @Test
    void deveAceitarValorLiquidoZero() {
        assertEquals(1_200L, frete("PR", 1_000, false, false, COMUM, 0));
    }

    // =====================================================================
    // Base por UF (switch: PR, SP/RJ agrupados, default)
    // =====================================================================

    @ParameterizedTest(name = "UF {0}, 1 kg, comum, sem adicionais -> {1}")
    @CsvSource({
        "PR, 1200",
        "SP, 2000",
        "RJ, 2000",
        "MG, 3000",
        "SC, 3000",
        "ZZ, 3000",   // qualquer UF no formato válido usa a tarifa padrão
        "AA, 3000"
    })
    void deveAplicarTarifaBasePorUf(String uf, long esperado) {
        assertEquals(esperado, frete(uf, 1_000, false, false, COMUM, 0));
    }

    // =====================================================================
    // Peso excedente (while): zero, uma e várias iterações; kg exato e fração
    // =====================================================================

    @ParameterizedTest(name = "PR, peso {0} g -> {1}")
    @CsvSource({
        "1,      1200",   // peso mínimo, sem laço
        "1999,   1200",   // logo abaixo de 2 kg: 0 iterações
        "2000,   1200",   // exatamente 2 kg: excedente 0, 0 iterações
        "2001,   1500",   // 1 g acima: 1 iteração (fração conta como kg cheio)
        "2999,   1500",   // excedente 999 g: 1 iteração
        "3000,   1500",   // excedente exatamente 1 kg: 1 iteração
        "3001,   1800",   // excedente 1.001 g: 2 iterações
        "4000,   1800",   // excedente exatamente 2 kg: 2 iterações
        "4001,   2100",   // 3 iterações
        "5000,   2100",   // excedente 3 kg exatos: 3 iterações
        "100000, 30600"   // excedente 98.000 g: 98 iterações (98 x 300 = 29.400)
    })
    void deveAcrescentarTrezentosPorKgAdicionalOuFracao(int peso, long esperado) {
        assertEquals(esperado, frete("PR", peso, false, false, COMUM, 0));
    }

    @Test
    void deveSomarPesoDeVariasLinhasEQuantidadesAntesDeAplicarOExcedente() {
        // 3 x 800 g + 1 x 1.000 g = 3.400 g -> excedente 1.400 g -> 2 iterações.
        List<ItemPedido> itens = List.of(
            new ItemPedido("A", 100, 3, 3, 800, false),
            new ItemPedido("B", 100, 1, 1, 1_000, false));
        Pedido pedido = new Pedido(itens, "SP", false, null);

        assertEquals(2_000L + 600L, calculadora.calcular(pedido, COMUM, 0));
    }

    @Test
    void deveCobrarExcedenteNasTarifasDeTodasAsUfs() {
        // 3.500 g -> 2 iterações (+600) em qualquer UF.
        assertAll(
            () -> assertEquals(1_800L, frete("PR", 3_500, false, false, COMUM, 0)),
            () -> assertEquals(2_600L, frete("SP", 3_500, false, false, COMUM, 0)),
            () -> assertEquals(2_600L, frete("RJ", 3_500, false, false, COMUM, 0)),
            () -> assertEquals(3_600L, frete("MG", 3_500, false, false, COMUM, 0))
        );
    }

    @Test
    void naoDeveConsiderarPesoDeLinhaInativaNoExcedente() {
        List<ItemPedido> itens = List.of(
            new ItemPedido("A", 100, 1, 1, 1_000, false),
            new ItemPedido("B", 100, 0, 0, 100_000, false)); // inativa: peso 0 no cálculo
        Pedido pedido = new Pedido(itens, "PR", false, null);

        assertEquals(1_200L, calculadora.calcular(pedido, COMUM, 0));
    }

    // =====================================================================
    // Frete grátis: líquido >= 30.000 e entrega normal
    // =====================================================================

    @Test
    void naoDeveZerarFreteUmCentavoAbaixoDoLimiar() {
        assertEquals(1_200L, frete("PR", 1_000, false, false, COMUM, ABAIXO_DO_LIMIAR_GRATIS));
    }

    @Test
    void deveZerarFreteExatamenteNoLimiar() {
        assertEquals(0L, frete("PR", 1_000, false, false, COMUM, NO_LIMIAR_GRATIS));
    }

    @Test
    void deveZerarFreteAcimaDoLimiar() {
        assertEquals(0L, frete("PR", 1_000, false, false, COMUM, 1_000_000));
    }

    @Test
    void deveZerarTambemOAdicionalDePesoNoFreteGratis() {
        // 5 kg em MG seria 3.000 + 900, mas com líquido >= 30.000 e entrega normal fica 0.
        assertEquals(0L, frete("MG", 5_000, false, false, COMUM, NO_LIMIAR_GRATIS));
    }

    @Test
    void naoDeveZerarFreteQuandoEntregaEExpressa() {
        // Expresso anula a gratuidade: 1.200 (base) + 1.500 (expresso).
        assertEquals(2_700L, frete("PR", 1_000, true, false, COMUM, NO_LIMIAR_GRATIS));
    }

    @Test
    void deveManterAdicionalDePesoQuandoExpressoAnulaGratuidade() {
        // 3.001 g -> 2 iterações: (1.200 + 600) + 1.500.
        assertEquals(3_300L, frete("PR", 3_001, true, false, COMUM, NO_LIMIAR_GRATIS));
    }

    // =====================================================================
    // VIP: metade do valor (depois da gratuidade)
    // =====================================================================

    @Test
    void deveCobrarMetadeDoFreteDoParanaParaVip() {
        assertEquals(600L, frete("PR", 1_000, false, false, VIP, 0));
    }

    @Test
    void deveCobrarMetadeDoFreteDeOutrasUfsParaVip() {
        assertAll(
            () -> assertEquals(1_000L, frete("SP", 1_000, false, false, VIP, 0)),
            () -> assertEquals(1_000L, frete("RJ", 1_000, false, false, VIP, 0)),
            () -> assertEquals(1_500L, frete("MG", 1_000, false, false, VIP, 0))
        );
    }

    @Test
    void deveCobrarMetadeTambemDoAdicionalDePesoParaVip() {
        // PR, 3.001 g: (1.200 + 600) / 2 = 900.
        assertEquals(900L, frete("PR", 3_001, false, false, VIP, 0));
    }

    @Test
    void deveManterFreteZeroParaVipQuandoGratuito() {
        assertEquals(0L, frete("MG", 3_500, false, false, VIP, NO_LIMIAR_GRATIS));
    }

    @Test
    void naoDeveDividirOAdicionalDeExpressoParaVip() {
        // PR: 1.200 / 2 = 600, depois + 1.500 (o adicional do expresso não é reduzido).
        assertEquals(2_100L, frete("PR", 1_000, true, false, VIP, 0));
    }

    @Test
    void naoDeveDividirOAdicionalDeFragilParaVip() {
        // PR: 600 + 500.
        assertEquals(1_100L, frete("PR", 1_000, false, true, VIP, 0));
    }

    // =====================================================================
    // Adicionais: expresso (+1.500) e frágil (+500 uma única vez)
    // =====================================================================

    @Test
    void deveAcrescentarQuinzeReaisNoExpresso() {
        assertEquals(2_700L, frete("PR", 1_000, true, false, COMUM, 0));
    }

    @Test
    void deveAcrescentarCincoReaisQuandoHaItemFragilAtivo() {
        assertEquals(1_700L, frete("PR", 1_000, false, true, COMUM, 0));
    }

    @Test
    void deveCobrarFragilUmaUnicaVezMesmoComVariasLinhasFrageis() {
        List<ItemPedido> itens = List.of(
            new ItemPedido("A", 100, 1, 1, 100, true),
            new ItemPedido("B", 100, 2, 2, 100, true),
            new ItemPedido("C", 100, 1, 1, 100, true));
        Pedido pedido = new Pedido(itens, "PR", false, null);

        assertEquals(1_200L + 500L, calculadora.calcular(pedido, COMUM, 0));
    }

    @Test
    void naoDeveCobrarFragilParaItemFragilInativo() {
        List<ItemPedido> itens = List.of(
            new ItemPedido("A", 100, 1, 1, 100, false),
            new ItemPedido("B", 100, 0, 0, 100, true));
        Pedido pedido = new Pedido(itens, "PR", false, null);

        assertEquals(1_200L, calculadora.calcular(pedido, COMUM, 0));
    }

    @Test
    void deveCobrarAdicionalDeFragilMesmoQuandoBaseFoiZerada() {
        assertEquals(500L, frete("PR", 1_000, false, true, COMUM, NO_LIMIAR_GRATIS));
    }

    @Test
    void deveCobrarAdicionalDeFragilParaVipMesmoQuandoBaseFoiZerada() {
        assertEquals(500L, frete("SP", 1_000, false, true, VIP, NO_LIMIAR_GRATIS));
    }

    @Test
    void deveCombinarTodosOsFatoresNoMesmoCalculo() {
        // PR, 3.001 g (2 iterações), expresso (sem gratuidade), VIP, frágil:
        // (1.200 + 600) / 2 = 900; + 1.500 = 2.400; + 500 = 2.900.
        assertEquals(2_900L, frete("PR", 3_001, true, true, VIP, NO_LIMIAR_GRATIS));
    }

    // =====================================================================
    // Decisões independentes: gratuidade x VIP x expresso x frágil (PR, 1 kg)
    // Tabela completa das 16 combinações, com o oráculo calculado à mão.
    // =====================================================================

    @ParameterizedTest(name = "gratis={0} vip={1} expresso={2} fragil={3} -> {4}")
    @CsvSource({
        // liquido >= 30.000?  vip  expresso  frágil  esperado
        "false, false, false, false, 1200",
        "false, false, false, true,  1700",
        "false, false, true,  false, 2700",
        "false, false, true,  true,  3200",
        "false, true,  false, false, 600",
        "false, true,  false, true,  1100",
        "false, true,  true,  false, 2100",
        "false, true,  true,  true,  2600",
        "true,  false, false, false, 0",
        "true,  false, false, true,  500",
        "true,  false, true,  false, 2700",   // expresso anula a gratuidade
        "true,  false, true,  true,  3200",
        "true,  true,  false, false, 0",
        "true,  true,  false, true,  500",
        "true,  true,  true,  false, 2100",
        "true,  true,  true,  true,  2600"
    })
    void deveCobrirTodasAsCombinacoesDeGratuidadeVipExpressoEFragil(
            boolean gratis, boolean vip, boolean expresso, boolean fragil, long esperado) {
        Cliente cliente = vip ? VIP : COMUM;
        long liquido = gratis ? NO_LIMIAR_GRATIS : 0;

        assertEquals(esperado, frete("PR", 1_000, expresso, fragil, cliente, liquido));
    }
}
