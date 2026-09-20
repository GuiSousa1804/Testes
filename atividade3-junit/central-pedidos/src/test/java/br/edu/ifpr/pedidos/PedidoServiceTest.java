package br.edu.ifpr.pedidos;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PedidoServiceTest {

    // ---------------------------------------------------------------------
    // Apoio: um processador que registra cada cobrança e devolve o roteiro dado
    // (Boolean = resposta; RuntimeException = falha lançada naquela chamada).
    // ---------------------------------------------------------------------
    private static final class ProcessadorFalso implements ProcessadorPagamento {
        final List<Long> cobrancas = new ArrayList<>();
        private final Object[] roteiro;

        ProcessadorFalso(Object... roteiro) {
            this.roteiro = roteiro;
        }

        @Override
        public boolean autorizar(long totalCentavos) {
            int chamada = cobrancas.size();
            cobrancas.add(totalCentavos);
            Object passo = roteiro.length == 0 ? Boolean.TRUE : roteiro[Math.min(chamada, roteiro.length - 1)];
            if (passo instanceof RuntimeException excecao) {
                throw excecao;
            }
            return (Boolean) passo;
        }
    }

    private static final Cliente COMUM_ANTIGO = new Cliente(false, false, 1);
    private static final Cliente COMUM_NOVO   = new Cliente(false, false, 0);
    private static final Cliente VIP_ANTIGO   = new Cliente(true, false, 3);
    private static final Cliente BLOQUEADO    = new Cliente(false, true, 2);

    // item(preco, quantidade, estoque, pesoUnitario, fragil)
    private static ItemPedido item(long preco, int quantidade, int estoque, int peso, boolean fragil) {
        return new ItemPedido("SKU", preco, quantidade, estoque, peso, fragil);
    }

    private static Pedido pedido(String uf, boolean expresso, String cupom, ItemPedido... itens) {
        return new Pedido(List.of(itens), uf, expresso, cupom);
    }

    private static void assertResultado(ResultadoPedido r, String status,
                                        long subtotal, long desconto, long frete, long total) {
        assertAll(
            () -> assertEquals(status, r.status(), "status"),
            () -> assertEquals(subtotal, r.subtotalCentavos(), "subtotal"),
            () -> assertEquals(desconto, r.descontoCentavos(), "desconto"),
            () -> assertEquals(frete, r.freteCentavos(), "frete"),
            () -> assertEquals(total, r.totalCentavos(), "total")
        );
    }

    @Test
    void deveFecharPedidoDeClienteComumComFreteDoParanaEPagamentoAprovado() {
        // 1. Preparar: cliente comum, uma compra anterior e item disponível de R$ 100,00.
        Cliente cliente = new Cliente(false, false, 1);
        ItemPedido item = new ItemPedido("LIVRO-JAVA", 10_000, 1, 5, 1_000, false);
        Pedido pedido = new Pedido(List.of(item), "PR", false, null);

        // Simula o pagamento e registra as cobranças, sem banco ou serviço externo.
        List<Long> cobrancas = new ArrayList<>();
        PedidoService service = new PedidoService(total -> {
            cobrancas.add(total);
            return true;
        });

        // 2. Executar: percorrer um caminho completo do fechamento.
        ResultadoPedido resultado = service.fechar(pedido, cliente);

        // 3. Verificar: sem desconto; frete de R$ 12,00; total de R$ 112,00.
        assertAll(
            () -> assertEquals("PAGO", resultado.status()),
            () -> assertEquals(10_000L, resultado.subtotalCentavos()),
            () -> assertEquals(0L, resultado.descontoCentavos()),
            () -> assertEquals(1_200L, resultado.freteCentavos()),
            () -> assertEquals(11_200L, resultado.totalCentavos()),
            // A lista comprova uma única cobrança, com o valor correto.
            () -> assertEquals(List.of(11_200L), cobrancas)
        );
    }

    // =====================================================================
    // Construção e referências obrigatórias
    // =====================================================================

    @Test
    void deveRejeitarProcessadorNuloNaConstrucao() {
        assertThrows(NullPointerException.class, () -> new PedidoService(null));
    }

    @Test
    void deveRejeitarPedidoNulo() {
        ProcessadorFalso processador = new ProcessadorFalso();

        assertThrows(NullPointerException.class, () -> new PedidoService(processador).fechar(null, COMUM_ANTIGO));
        assertTrue(processador.cobrancas.isEmpty());
    }

    @Test
    void deveRejeitarClienteNulo() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        assertThrows(NullPointerException.class, () -> new PedidoService(processador).fechar(pedido, null));
        assertTrue(processador.cobrancas.isEmpty());
    }

    // =====================================================================
    // Cliente bloqueado: retorno antecipado, antes de itens e cupom
    // =====================================================================

    @Test
    void deveBloquearClienteBloqueadoSemCobrarNada() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, BLOQUEADO);

        assertResultado(resultado, "BLOQUEADO", 0, 0, 0, 0);
        assertTrue(processador.cobrancas.isEmpty());
    }

    @Test
    void deveBloquearAntesDeValidarSubtotalZero() {
        // Lista vazia lançaria IllegalArgumentException para cliente liberado.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedidoVazio = new Pedido(List.of(), "PR", false, null);

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedidoVazio, BLOQUEADO);

        assertResultado(resultado, "BLOQUEADO", 0, 0, 0, 0);
    }

    @Test
    void deveBloquearAntesDeVerificarEstoque() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido semEstoque = pedido("PR", false, null, item(10_000, 5, 1, 1_000, false));

        assertResultado(new PedidoService(processador).fechar(semEstoque, BLOQUEADO), "BLOQUEADO", 0, 0, 0, 0);
    }

    @Test
    void deveBloquearAntesDeAvaliarCupomInvalido() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido cupomInvalido = pedido("PR", false, "NAOEXISTE", item(10_000, 1, 5, 1_000, false));

        assertDoesNotThrow(() -> new PedidoService(processador).fechar(cupomInvalido, BLOQUEADO));
        assertTrue(processador.cobrancas.isEmpty());
    }

    // =====================================================================
    // Subtotal zero
    // =====================================================================

    @Test
    void deveRejeitarPedidoSemLinhas() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido vazio = new Pedido(List.of(), "PR", false, null);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> new PedidoService(processador).fechar(vazio, COMUM_ANTIGO));

        assertEquals("Pedido sem itens ativos", erro.getMessage());
        assertTrue(processador.cobrancas.isEmpty());
    }

    @Test
    void deveRejeitarPedidoSomenteComLinhasInativas() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido inativas = pedido("PR", false, null,
            item(10_000, 0, 0, 1_000, false), item(5_000, 0, 0, 500, true));

        assertThrows(IllegalArgumentException.class,
            () -> new PedidoService(processador).fechar(inativas, COMUM_ANTIGO));
        assertTrue(processador.cobrancas.isEmpty());
    }

    // =====================================================================
    // Estoque: retorno antecipado, antes do cupom
    // =====================================================================

    @Test
    void deveRetornarSemEstoqueQuandoQuantidadeExcedeOEstoque() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(10_000, 3, 2, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "SEM_ESTOQUE", 0, 0, 0, 0);
        assertTrue(processador.cobrancas.isEmpty());
    }

    @Test
    void deveRetornarSemEstoqueMesmoQuandoSoAUltimaLinhaFalta() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null,
            item(10_000, 1, 5, 1_000, false), item(2_000, 4, 3, 100, false));

        assertResultado(new PedidoService(processador).fechar(pedido, COMUM_ANTIGO), "SEM_ESTOQUE", 0, 0, 0, 0);
    }

    @Test
    void deveRetornarSemEstoqueAntesDeAvaliarCupomDesconhecido() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, "NAOEXISTE", item(10_000, 3, 2, 1_000, false));

        ResultadoPedido resultado = assertDoesNotThrow(
            () -> new PedidoService(processador).fechar(pedido, COMUM_ANTIGO));

        assertResultado(resultado, "SEM_ESTOQUE", 0, 0, 0, 0);
    }

    @Test
    void deveAceitarQuantidadeIgualAoEstoque() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(10_000, 5, 5, 400, false));

        assertEquals("PAGO", new PedidoService(processador).fechar(pedido, COMUM_ANTIGO).status());
    }

    @Test
    void deveAvaliarEstoquePorLinhaQuandoOSkuSeRepete() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null,
            item(1_000, 3, 3, 100, false), item(1_000, 3, 3, 100, false));

        assertEquals("PAGO", new PedidoService(processador).fechar(pedido, COMUM_ANTIGO).status());
    }

    // =====================================================================
    // Cupom desconhecido: exceção depois do estoque e antes do pagamento
    // =====================================================================

    @Test
    void deveLancarExcecaoParaCupomDesconhecidoSemCobrar() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, "PROMO", item(10_000, 1, 5, 1_000, false));

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> new PedidoService(processador).fechar(pedido, COMUM_ANTIGO));

        assertEquals("Cupom desconhecido", erro.getMessage());
        assertTrue(processador.cobrancas.isEmpty());
    }

    // =====================================================================
    // Caminhos completos: desconto + frete + risco + pagamento
    // =====================================================================

    @Test
    void deveIgnorarCupomBrancoNoFechamento() {
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, "   ", item(10_000, 1, 5, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "PAGO", 10_000, 0, 1_200, 11_200);
    }

    @Test
    void deveCalcularVipComExtra10AtingindoOTetoEFreteGratis() {
        // Subtotal 2 x 25.000 = 50.000. VIP 10% = 5.000 + EXTRA10 5.000 = 10.000 = teto (20%).
        // Líquido 40.000 >= 30.000, entrega normal: frete zero. Total 40.000. Risco: APROVADO.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("SP", false, " extra10 ", item(25_000, 2, 5, 1_500, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, VIP_ANTIGO);

        assertResultado(resultado, "PAGO", 50_000, 10_000, 0, 40_000);
        assertEquals(List.of(40_000L), processador.cobrancas);
    }

    @Test
    void deveAplicarCincoPorCentoAClienteComumEFreteGratisAcimaDeTrezentosReais() {
        // 60.000: desconto 5% = 3.000; líquido 57.000; frete grátis; total 57.000.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("RJ", false, null, item(60_000, 1, 1, 500, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "PAGO", 60_000, 3_000, 0, 57_000);
        assertEquals(List.of(57_000L), processador.cobrancas);
    }

    @Test
    void deveCobrarFreteBaseMaisExcedenteDePesoForaDoParana() {
        // 3 x 5.000 = 15.000, sem desconto. Peso 3 x 1.500 = 4.500 g: excedente 2.500 g -> 3 iterações.
        // MG: 3.000 + 900 = 3.900. Total 18.900.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("MG", false, null, item(5_000, 3, 3, 1_500, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "PAGO", 15_000, 0, 3_900, 18_900);
        assertEquals(List.of(18_900L), processador.cobrancas);
    }

    @Test
    void deveCombinarVipExpressoEFragilNoFrete() {
        // 10.000: VIP 10% = 1.000; líquido 9.000. PR 1.200 / 2 = 600; + 1.500 expresso + 500 frágil = 2.600.
        // Total 11.600. Cliente antigo: risco APROVADO.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", true, null, item(10_000, 1, 1, 1_000, true));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, VIP_ANTIGO);

        assertResultado(resultado, "PAGO", 10_000, 1_000, 2_600, 11_600);
        assertEquals(List.of(11_600L), processador.cobrancas);
    }

    @Test
    void deveTruncarCentavosNoDescontoDoClienteVip() {
        // 12.345: 10% = 1.234,5 -> 1.234; líquido 11.111; PR VIP 1.200 / 2 = 600; total 11.711.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(12_345, 1, 1, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, VIP_ANTIGO);

        assertResultado(resultado, "PAGO", 12_345, 1_234, 600, 11_711);
    }

    @Test
    void deveAplicarBemvindoNaPrimeiraCompraDentroDoLimiteDeRisco() {
        // 20.000, primeira compra, BEMVINDO: desconto 2.000; líquido 18.000; PR 1.200; total 19.200 (<= 100.000).
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, "bemvindo", item(20_000, 1, 1, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_NOVO);

        assertResultado(resultado, "PAGO", 20_000, 2_000, 1_200, 19_200);
    }

    @Test
    void deveIgnorarLinhasInativasNoValorNoPesoENaFragilidade() {
        // A linha inativa tem preço, peso e fragilidade "altos", mas quantidade zero: não influencia nada.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null,
            item(10_000, 1, 5, 1_000, false),
            item(999_999, 0, 0, 100_000, true));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "PAGO", 10_000, 0, 1_200, 11_200);
    }

    // =====================================================================
    // Risco: retornos antecipados sem cobrança
    // =====================================================================

    @Test
    void deveEnviarPrimeiraCompraAcimaDeMilReaisParaRevisaoSemCobrar() {
        // 2 x 60.000 = 120.000; 5% = 6.000; líquido 114.000; frete grátis; total 114.000 > 100.000.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(60_000, 2, 2, 500, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_NOVO);

        assertResultado(resultado, "REVISAO", 120_000, 6_000, 0, 114_000);
        assertTrue(processador.cobrancas.isEmpty());
    }

    @Test
    void deveEnviarPrimeiraCompraExpressaParaRevisaoSemCobrar() {
        // 10.000 + frete 1.200 + expresso 1.500 = 12.700 (total baixo, mas expresso em 1ª compra).
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", true, null, item(10_000, 1, 1, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_NOVO);

        assertResultado(resultado, "REVISAO", 10_000, 0, 2_700, 12_700);
        assertTrue(processador.cobrancas.isEmpty());
    }

    @Test
    void deveEnviarClienteComumAntigoParaRevisaoQuandoTotalPassaDeCincoMilReais() {
        // 1.000.000 x 1: 5% = 50.000; líquido 950.000; frete grátis; total 950.000 > 500.000.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(1_000_000, 1, 1, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "REVISAO", 1_000_000, 50_000, 0, 950_000);
        assertTrue(processador.cobrancas.isEmpty());
    }

    @Test
    void deveAprovarClienteVipAntigoComOMesmoPedidoDeAltoValor() {
        // Mesmo pedido acima, mas VIP: 10% = 100.000; total 900.000; VIP antigo não vai para revisão.
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(1_000_000, 1, 1, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, VIP_ANTIGO);

        assertResultado(resultado, "PAGO", 1_000_000, 100_000, 0, 900_000);
        assertEquals(List.of(900_000L), processador.cobrancas);
    }

    @Test
    void deveAprovarPrimeiraCompraDeClienteVipComTotalAbaixoDoLimiteDeRisco() {
        // Preço 100.000, VIP 10% = 10.000; líquido 90.000 (>= 30.000: frete grátis); total 90.000 (< 100.000).
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido("PR", false, null, item(100_000, 1, 1, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador)
            .fechar(pedido, new Cliente(true, false, 0));

        assertResultado(resultado, "PAGO", 100_000, 10_000, 0, 90_000);
    }

    // =====================================================================
    // Pagamento: recusa, indisponibilidade, esgotamento e propagação
    // =====================================================================

    @Test
    void deveRetornarPagamentoRecusadoComOsValoresCalculados() {
        ProcessadorFalso processador = new ProcessadorFalso(false);
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "PAGAMENTO_RECUSADO", 10_000, 0, 1_200, 11_200);
        assertEquals(List.of(11_200L), processador.cobrancas, "recusa definitiva não repete");
    }

    @Test
    void deveRepetirAposIndisponibilidadeEPagarNaSegundaTentativa() {
        ProcessadorFalso processador = new ProcessadorFalso(new IllegalStateException("fora do ar"), true);
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "PAGO", 10_000, 0, 1_200, 11_200);
        assertEquals(List.of(11_200L, 11_200L), processador.cobrancas);
    }

    @Test
    void devePagarNaTerceiraTentativaAposDuasIndisponibilidades() {
        ProcessadorFalso processador = new ProcessadorFalso(
            new IllegalStateException("1"), new IllegalStateException("2"), true);
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertEquals("PAGO", resultado.status());
        assertEquals(3, processador.cobrancas.size());
    }

    @Test
    void deveRetornarPagamentoRecusadoAoEsgotarTresTentativasIndisponiveis() {
        ProcessadorFalso processador = new ProcessadorFalso(new IllegalStateException("fora do ar"));
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertResultado(resultado, "PAGAMENTO_RECUSADO", 10_000, 0, 1_200, 11_200);
        assertEquals(List.of(11_200L, 11_200L, 11_200L), processador.cobrancas);
    }

    @Test
    void devePropagarExcecaoInesperadaDoProcessadorSemAlterarOFechamento() {
        ProcessadorFalso processador = new ProcessadorFalso(new UnsupportedOperationException("falha"));
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        assertThrows(UnsupportedOperationException.class,
            () -> new PedidoService(processador).fechar(pedido, COMUM_ANTIGO));
        assertEquals(1, processador.cobrancas.size());
    }

    // =====================================================================
    // Independência entre fechamentos (simulação, sem estado)
    // =====================================================================

    @Test
    void deveProduzirOMesmoResultadoEmFechamentosRepetidos() {
        ProcessadorFalso processador = new ProcessadorFalso();
        PedidoService service = new PedidoService(processador);
        Pedido pedido = pedido("PR", false, null, item(10_000, 1, 5, 1_000, false));

        ResultadoPedido primeiro = service.fechar(pedido, COMUM_ANTIGO);
        ResultadoPedido segundo = service.fechar(pedido, COMUM_ANTIGO);

        assertEquals(primeiro, segundo);
        assertEquals(List.of(11_200L, 11_200L), processador.cobrancas);
    }

    @ParameterizedTest(name = "UF {0}: frete-base de pedido pequeno")
    @ValueSource(strings = {"PR", "SP", "RJ", "MG"})
    void deveUsarATarifaDaUfInformadaNoFechamento(String uf) {
        long esperado = switch (uf) {
            case "PR" -> 1_200L;
            case "SP", "RJ" -> 2_000L;
            default -> 3_000L;
        };
        ProcessadorFalso processador = new ProcessadorFalso();
        Pedido pedido = pedido(uf, false, null, item(10_000, 1, 5, 1_000, false));

        ResultadoPedido resultado = new PedidoService(processador).fechar(pedido, COMUM_ANTIGO);

        assertEquals(esperado, resultado.freteCentavos());
        assertEquals(10_000 + esperado, resultado.totalCentavos());
    }
}
