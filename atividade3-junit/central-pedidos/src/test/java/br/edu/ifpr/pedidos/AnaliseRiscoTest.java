package br.edu.ifpr.pedidos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras de AnaliseRisco.avaliar(cliente, total, expresso), na ordem do código:
 * - total negativo: IllegalArgumentException;
 * - cliente bloqueado: RECUSADO;
 * - sem compras anteriores: REVISAO se total > 100.000 (R$ 1.000,00) OU entrega expressa;
 * - com compras anteriores: REVISAO se total > 500.000 (R$ 5.000,00) E cliente não VIP;
 * - caso contrário: APROVADO.
 *
 * Nota: RECUSADO só é alcançável aqui (teste unitário). No PedidoService o cliente bloqueado
 * é barrado antes de chegar à análise de risco.
 */
class AnaliseRiscoTest {

    private final AnaliseRisco risco = new AnaliseRisco();

    // cliente(vip, bloqueado, compras)
    private static Cliente cliente(boolean vip, boolean bloqueado, int compras) {
        return new Cliente(vip, bloqueado, compras);
    }

    // =====================================================================
    // Validação e bloqueio
    // =====================================================================

    @ParameterizedTest(name = "total {0} deve ser rejeitado")
    @ValueSource(longs = {-1, -100_000, Long.MIN_VALUE})
    void deveRejeitarTotalNegativo(long total) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> risco.avaliar(cliente(false, false, 1), total, false));

        assertEquals("Total negativo", erro.getMessage());
    }

    @Test
    void deveRejeitarTotalNegativoAntesDeVerificarBloqueio() {
        assertThrows(IllegalArgumentException.class,
            () -> risco.avaliar(cliente(false, true, 0), -1, false));
    }

    @Test
    void deveRecusarClienteBloqueadoSemCompras() {
        assertEquals("RECUSADO", risco.avaliar(cliente(false, true, 0), 100, false));
    }

    @Test
    void deveRecusarClienteBloqueadoComCompras() {
        assertEquals("RECUSADO", risco.avaliar(cliente(false, true, 5), 100, false));
    }

    @Test
    void deveRecusarClienteBloqueadoVipMesmoComTotalZero() {
        assertEquals("RECUSADO", risco.avaliar(cliente(true, true, 5), 0, false));
    }

    @Test
    void deveRecusarClienteBloqueadoMesmoComEntregaExpressaETotalAlto() {
        // O bloqueio prevalece sobre qualquer outra regra (retorno antecipado).
        assertEquals("RECUSADO", risco.avaliar(cliente(false, true, 0), 900_000, true));
    }

    // =====================================================================
    // Cliente sem compras anteriores: total > 100.000 OU expresso
    // =====================================================================

    @Test
    void deveAprovarPrimeiraCompraComTotalNoLimiteENormal() {
        // Exatamente R$ 1.000,00 não é "maior que" o limite.
        assertEquals("APROVADO", risco.avaliar(cliente(false, false, 0), 100_000, false));
    }

    @Test
    void deveEnviarPrimeiraCompraParaRevisaoUmCentavoAcimaDoLimite() {
        assertEquals("REVISAO", risco.avaliar(cliente(false, false, 0), 100_001, false));
    }

    @Test
    void deveAprovarPrimeiraCompraComTotalZeroENormal() {
        assertEquals("APROVADO", risco.avaliar(cliente(false, false, 0), 0, false));
    }

    @Test
    void deveEnviarPrimeiraCompraExpressaParaRevisaoMesmoComTotalBaixo() {
        assertEquals("REVISAO", risco.avaliar(cliente(false, false, 0), 1, true));
    }

    @Test
    void deveEnviarPrimeiraCompraExpressaParaRevisaoNoLimite() {
        assertEquals("REVISAO", risco.avaliar(cliente(false, false, 0), 100_000, true));
    }

    @Test
    void deveEnviarPrimeiraCompraExpressaEAcimaDoLimiteParaRevisao() {
        // Ambos os operandos do || são verdadeiros.
        assertEquals("REVISAO", risco.avaliar(cliente(false, false, 0), 100_001, true));
    }

    @Test
    void deveAplicarRegraDePrimeiraCompraTambemAoClienteVip() {
        assertAll(
            () -> assertEquals("REVISAO", risco.avaliar(cliente(true, false, 0), 100_001, false)),
            () -> assertEquals("REVISAO", risco.avaliar(cliente(true, false, 0), 1, true)),
            () -> assertEquals("APROVADO", risco.avaliar(cliente(true, false, 0), 100_000, false))
        );
    }

    // =====================================================================
    // Cliente com compras anteriores: total > 500.000 E não VIP
    // =====================================================================

    @Test
    void deveAprovarClienteComumAntigoComTotalNoLimite() {
        assertEquals("APROVADO", risco.avaliar(cliente(false, false, 1), 500_000, false));
    }

    @Test
    void deveEnviarClienteComumAntigoParaRevisaoUmCentavoAcimaDoLimite() {
        assertEquals("REVISAO", risco.avaliar(cliente(false, false, 1), 500_001, false));
    }

    @Test
    void deveAprovarClienteVipAntigoMesmoAcimaDoLimite() {
        assertEquals("APROVADO", risco.avaliar(cliente(true, false, 1), 500_001, false));
    }

    @Test
    void deveAprovarClienteVipAntigoComTotalAltissimo() {
        assertEquals("APROVADO", risco.avaliar(cliente(true, false, 10), 10_000_000_000L, false));
    }

    @Test
    void deveEnviarClienteComumAntigoParaRevisaoComTotalAltissimo() {
        assertEquals("REVISAO", risco.avaliar(cliente(false, false, 10), 10_000_000_000L, false));
    }

    @Test
    void naoDeveAplicarLimiteDePrimeiraCompraAClienteAntigo() {
        // 100.001 exigiria revisão numa primeira compra, mas não para quem já comprou.
        assertEquals("APROVADO", risco.avaliar(cliente(false, false, 1), 100_001, false));
    }

    @Test
    void naoDeveExigirRevisaoDeEntregaExpressaParaClienteAntigo() {
        assertAll(
            () -> assertEquals("APROVADO", risco.avaliar(cliente(false, false, 1), 1, true)),
            () -> assertEquals("APROVADO", risco.avaliar(cliente(true, false, 1), 1, true)),
            () -> assertEquals("APROVADO", risco.avaliar(cliente(false, false, 3), 500_000, true))
        );
    }

    @Test
    void deveEnviarClienteAntigoExpressoParaRevisaoQuandoTotalPassaDoLimiteENaoEVip() {
        assertEquals("REVISAO", risco.avaliar(cliente(false, false, 2), 500_001, true));
    }

    // =====================================================================
    // Tabela de decisão: compras anteriores x VIP x expresso x faixa de total
    // (bloqueado = false). Faixas: BAIXO (50.000), MEDIO (300.000), ALTO (600.000).
    // =====================================================================

    @ParameterizedTest(name = "compras={0} vip={1} expresso={2} total={3} -> {4}")
    @CsvSource({
        // sem compras: revisão se total > 100.000 ou expresso
        "0, false, false, 50000,  APROVADO",
        "0, false, false, 300000, REVISAO",
        "0, false, false, 600000, REVISAO",
        "0, false, true,  50000,  REVISAO",
        "0, false, true,  300000, REVISAO",
        "0, false, true,  600000, REVISAO",
        "0, true,  false, 50000,  APROVADO",
        "0, true,  false, 300000, REVISAO",
        "0, true,  false, 600000, REVISAO",
        "0, true,  true,  50000,  REVISAO",
        "0, true,  true,  600000, REVISAO",
        // com compras: revisão só se total > 500.000 e não VIP
        "2, false, false, 50000,  APROVADO",
        "2, false, false, 300000, APROVADO",
        "2, false, false, 600000, REVISAO",
        "2, false, true,  50000,  APROVADO",
        "2, false, true,  300000, APROVADO",
        "2, false, true,  600000, REVISAO",
        "2, true,  false, 50000,  APROVADO",
        "2, true,  false, 300000, APROVADO",
        "2, true,  false, 600000, APROVADO",
        "2, true,  true,  50000,  APROVADO",
        "2, true,  true,  600000, APROVADO"
    })
    void deveSeguirATabelaDeDecisaoDoRisco(int compras, boolean vip, boolean expresso, long total, String esperado) {
        assertEquals(esperado, risco.avaliar(cliente(vip, false, compras), total, expresso));
    }
}
