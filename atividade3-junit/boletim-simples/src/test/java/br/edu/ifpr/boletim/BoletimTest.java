package br.edu.ifpr.boletim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BoletimTest {

    @Test
    void deveAprovarAlunoComMediaOito() {
        // Preparar: criar o objeto que será testado.
        Boletim boletim = new Boletim();

        // Executar: chamar um único método com uma entrada conhecida.
        String resultado = boletim.verificarSituacao(8);

        // Verificar: comparar o resultado esperado com o resultado obtido.
        assertEquals("APROVADO", resultado);
    }

    @Test
    void deveRecuperarNotaAlunoComMediaQuatro() {
        Boletim boletim = new Boletim();

        // Executar: chamar um único método com uma entrada conhecida.
        String resultado = boletim.verificarSituacao(4);

        // Verificar: comparar o resultado esperado com o resultado obtido.
        assertEquals("RECUPERACAO", resultado);
    }

    @Test
    void deveReprovarAlunoComMediaDois() {
        Boletim boletim = new Boletim();

        // Executar: chamar um único método com uma entrada conhecida.
        String resultado = boletim.verificarSituacao(2);

        // Verificar: comparar o resultado esperado com o resultado obtido.
        assertEquals("REPROVADO", resultado);
    }

    @Test
    void deveCalcularMediaIgualCinco() {
        Boletim boletim = new Boletim();

        double resultado = boletim.calcularMedia(5,5);

        assertEquals(5,resultado);

    }
    // =====================================================================
    // verificarSituacao: dois if encadeados -> três caminhos (V(G) = 3)
    //   média >= 7 -> APROVADO | 4 <= média < 7 -> RECUPERACAO | média < 4 -> REPROVADO
    // Os valores 4 e 7 são as fronteiras; testamos abaixo, igual e acima de cada uma.
    // =====================================================================

    @ParameterizedTest(name = "média {0} -> {1}")
    @CsvSource({
        "10,    APROVADO",      // maior nota possível
        "7.01,  APROVADO",      // logo acima da fronteira 7
        "7,     APROVADO",      // exatamente na fronteira: >= 7
        "6.99,  RECUPERACAO",   // logo abaixo da fronteira 7
        "5.5,   RECUPERACAO",   // meio da faixa
        "4.01,  RECUPERACAO",   // logo acima da fronteira 4
        "4,     RECUPERACAO",   // exatamente na fronteira: >= 4
        "3.99,  REPROVADO",     // logo abaixo da fronteira 4
        "1,     REPROVADO",
        "0,     REPROVADO"      // menor nota possível
    })
    void deveClassificarSituacaoNasFaixasEFronteiras(double media, String esperado) {
        Boletim boletim = new Boletim();

        assertEquals(esperado, boletim.verificarSituacao(media));
    }

    // =====================================================================
    // calcularMedia: soma / 2, sem arredondamento.
    // Para double usamos a tolerância (terceiro argumento) do assertEquals.
    // =====================================================================

    @ParameterizedTest(name = "média de {0} e {1} = {2}")
    @CsvSource({
        "0,    0,    0",
        "10,   10,   10",
        "7,    8,    7.5",      // parte decimal
        "3,    4,    3.5",
        "6,    7,    6.5",
        "5.5,  6.5,  6",
        "10,   0,    5",        // notas extremas
        "0,    10,   5",        // a ordem das notas não importa
        "2.5,  3.75, 3.125",    // sem arredondamento
        "6.9,  7.1,  7"
    })
    void deveCalcularMediaAritmeticaSemArredondar(double nota1, double nota2, double esperada) {
        Boletim boletim = new Boletim();

        assertEquals(esperada, boletim.calcularMedia(nota1, nota2), 0.0001);
    }

    @Test
    void deveCalcularMediaComParteDecimalUsandoTolerancia() {
        Boletim boletim = new Boletim();

        double resultado = boletim.calcularMedia(7, 8);

        assertEquals(7.5, resultado, 0.0001);
    }

    @Test
    void deveIntegrarCalculoDeMediaEClassificacao() {
        // (6 + 8) / 2 = 7 -> exatamente a fronteira de aprovação.
        Boletim boletim = new Boletim();

        double media = boletim.calcularMedia(6, 8);

        assertEquals("APROVADO", boletim.verificarSituacao(media));
    }

    // =====================================================================
    // contarAprovados: for + if. Zero, uma e várias iterações; aprovados e não aprovados.
    // =====================================================================

    @Test
    void deveRetornarZeroParaArrayVazio() {
        // Zero iterações: o corpo do for nunca executa.
        Boletim boletim = new Boletim();

        assertEquals(0, boletim.contarAprovados(new double[] {}));
    }

    @Test
    void deveContarUmQuandoOUnicoElementoEstaAprovado() {
        // Uma iteração, if verdadeiro.
        Boletim boletim = new Boletim();

        assertEquals(1, boletim.contarAprovados(new double[] {8}));
    }

    @Test
    void deveContarZeroQuandoOUnicoElementoNaoEstaAprovado() {
        // Uma iteração, if falso.
        Boletim boletim = new Boletim();

        assertEquals(0, boletim.contarAprovados(new double[] {5}));
    }

    @Test
    void deveContarApenasOsAprovadosEmVariosElementos() {
        // Várias iterações, com if verdadeiro e falso: 8 e 7 aprovam; 5 não.
        Boletim boletim = new Boletim();

        assertEquals(2, boletim.contarAprovados(new double[] {8, 5, 7}));
    }

    @Test
    void deveContarTodosQuandoTodosEstaoAprovados() {
        Boletim boletim = new Boletim();

        assertEquals(3, boletim.contarAprovados(new double[] {9, 10, 8}));
    }

    @Test
    void deveContarZeroQuandoNenhumEstaAprovado() {
        Boletim boletim = new Boletim();

        assertEquals(0, boletim.contarAprovados(new double[] {1, 2, 3, 6.99}));
    }

    @Test
    void deveConsiderarSeteComoAprovadoEFronteiraDoContador() {
        Boletim boletim = new Boletim();

        assertEquals(1, boletim.contarAprovados(new double[] {7}));
        assertEquals(0, boletim.contarAprovados(new double[] {6.99}));
    }

    @Test
    void deveContarAprovadosNoInicioNoMeioENoFimDoArray() {
        Boletim boletim = new Boletim();

        assertEquals(3, boletim.contarAprovados(new double[] {9, 3, 8, 2, 7}));
    }
}
