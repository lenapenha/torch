import type { InputHTMLAttributes } from "react"

export default function Field({ label, id, ...inputProps }: {      
    label: string                                                  
    id: string                                                     
} & InputHTMLAttributes<HTMLInputElement>) {                       
    return (                                                       
        <div>                                                      
            <label htmlFor={id} className="block text-sm font-medium">{label}</label>                                       
            <input                                                 
                id={id}                                            
                {...inputProps}                                    
                className="mt-1 block w-full rounded border border-gray-300 bg-white px-3 py-2"                                
            />                                                     
        </div>                                                     
    )                                                              
} 