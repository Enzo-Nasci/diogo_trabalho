package exercicio1.precos.services;

public enum Product {
    FRANGO(10.0),
    PERU(18.0),
    CHESTER(22.0)
    ;
    

    
    private final double preco;


    private Product(double preco){
        this.preco = preco;
    }


    public double getPreco(){
        return preco;
    }
    


}
