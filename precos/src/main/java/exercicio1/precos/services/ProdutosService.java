package exercicio1.precos.services;

import lombok.Getter;

@Getter
public class ProdutosService {
    private final Product products;
    private final double quantity;

    public ProdutosService(Product products,double quantity){
        this.products = products;
        if (quantity<=0.0){
            throw new IllegalArgumentException("Quantidade deve ser valida");
        }
            this.quantity = quantity;
    }

    public boolean makeDiscount(){
        return this.quantity >=10.0;
    }


    public double totalPrice(){
        return makeDiscount()?this.products.getPreco()*this.quantity*0.95:this.products.getPreco()*this.quantity*1.0;
    }




}
